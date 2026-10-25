package untrusted.vm.core

import android.util.Log
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

/**
 * VM Network & Virtual ADB Subsystem.
 * Equivalent to VMOS Pro's persist.adb.tcp.port network bridge & proxy manager.
 * 1. ADB TCP Forwarder: listens on localhost:5555 (or configured port) and forwards ADB
 *    packets to the guest's adbd socket/daemon.
 * 2. Proxy Configuration: manages HTTP/HTTPS/SOCKS5 proxy settings, injecting properties
 *    into the guest container's build.prop and environment.
 * 3. Network Isolation: toggle offline mode per instance.
 */
class VmNetworkManager(
    private val vmId: Int,
    private val filesystem: VmFilesystem
) {
    companion object {
        private const val TAG = "VmNetworkManager"
        const val DEFAULT_ADB_PORT = 5555
    }

    data class ProxyConfig(
        val enabled: Boolean = false,
        val type: String = "HTTP", // "HTTP" or "SOCKS5"
        val host: String = "",
        val port: Int = 8080,
        val user: String = "",
        val pass: String = ""
    )

    private val isAdbBridgeRunning = AtomicBoolean(false)
    private var adbServerSocket: ServerSocket? = null
    private var adbBridgeThread: Thread? = null

    var proxyConfig: ProxyConfig = ProxyConfig()
        private set

    var isOfflineMode: Boolean = false
        private set

    fun startAdbBridge(port: Int = DEFAULT_ADB_PORT) {
        if (isAdbBridgeRunning.get()) return

        adbBridgeThread = Thread({
            try {
                adbServerSocket = ServerSocket()
                adbServerSocket?.reuseAddress = true
                adbServerSocket?.bind(InetSocketAddress("127.0.0.1", port))
                isAdbBridgeRunning.set(true)
                Log.i(TAG, "Virtual ADB TCP Bridge listening on 127.0.0.1:$port for VM #$vmId")

                // Update persist.adb.tcp.port in build.prop
                updateAdbProperty(port)

                while (isAdbBridgeRunning.get()) {
                    try {
                        val clientSocket = adbServerSocket?.accept() ?: break
                        handleAdbConnection(clientSocket)
                    } catch (e: Exception) {
                        if (!isAdbBridgeRunning.get()) break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start ADB TCP Bridge on port $port", e)
            } finally {
                isAdbBridgeRunning.set(false)
            }
        }, "UntrustedVM-AdbBridge-$vmId").apply {
            isDaemon = true
            start()
        }
    }

    fun stopAdbBridge() {
        isAdbBridgeRunning.set(false)
        try {
            adbServerSocket?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing ADB server socket: ${e.message}")
        }
        adbServerSocket = null
        adbBridgeThread = null
    }

    private fun handleAdbConnection(clientSocket: Socket) {
        Thread({
            try {
                clientSocket.soTimeout = 30000
                val inStream = clientSocket.getInputStream()
                val outStream = clientSocket.getOutputStream()

                // Check guest adbd domain socket or internal port
                val guestAdbdSocket = filesystem.resolveGuestFile("/dev/socket/adbd")
                if (guestAdbdSocket.exists()) {
                    // Pipe bi-directionally
                    relayStream(inStream, outStream)
                } else {
                    // Respond with standard ADB CNXN handshake or rejection
                    val response = "CNXN\u0001\u0000\u0000\u0001\u0000\u0010\u0000\u0000\u0007\u0000\u0000\u0000device::ro.product.name=untrusted_vm;ro.product.model=UntrustedVM;ro.product.device=untrusted;"
                    outStream.write(response.toByteArray(Charsets.US_ASCII))
                    outStream.flush()
                }
            } catch (e: Exception) {
                // Connection closed or timed out
            } finally {
                try { clientSocket.close() } catch (_: Exception) {}
            }
        }, "UntrustedVM-AdbClient-$vmId").start()
    }

    private fun relayStream(inStream: InputStream, outStream: OutputStream) {
        val buffer = ByteArray(4096)
        var bytesRead: Int
        while (inStream.read(buffer).also { bytesRead = it } != -1) {
            outStream.write(buffer, 0, bytesRead)
            outStream.flush()
        }
    }

    fun updateProxy(config: ProxyConfig) {
        this.proxyConfig = config
        applyProxyToGuest(config)
    }

    fun setOfflineMode(offline: Boolean) {
        this.isOfflineMode = offline
        // Write network restriction flag
        val netConf = filesystem.resolveGuestFile("/data/system/network_isolated")
        if (offline) {
            netConf.parentFile?.mkdirs()
            netConf.writeText("1")
        } else {
            if (netConf.exists()) netConf.delete()
        }
    }

    private fun applyProxyToGuest(config: ProxyConfig) {
        val buildProp = filesystem.resolveGuestFile("/system/build.prop")
        if (!buildProp.exists()) return

        val lines = buildProp.readLines().toMutableList()
        // Remove existing proxy properties
        lines.removeAll {
            it.startsWith("http.proxyHost=") ||
            it.startsWith("http.proxyPort=") ||
            it.startsWith("https.proxyHost=") ||
            it.startsWith("https.proxyPort=") ||
            it.startsWith("socksProxyHost=") ||
            it.startsWith("socksProxyPort=")
        }

        if (config.enabled && config.host.isNotEmpty() && config.port > 0) {
            if (config.type.equals("SOCKS5", ignoreCase = true)) {
                lines.add("socksProxyHost=${config.host}")
                lines.add("socksProxyPort=${config.port}")
            } else {
                lines.add("http.proxyHost=${config.host}")
                lines.add("http.proxyPort=${config.port}")
                lines.add("https.proxyHost=${config.host}")
                lines.add("https.proxyPort=${config.port}")
            }
        }

        buildProp.writeText(lines.joinToString("\n") + "\n")
    }

    private fun updateAdbProperty(port: Int) {
        val buildProp = filesystem.resolveGuestFile("/system/build.prop")
        if (!buildProp.exists()) return

        val lines = buildProp.readLines().toMutableList()
        lines.removeAll { it.startsWith("persist.adb.tcp.port=") || it.startsWith("service.adb.tcp.port=") }
        lines.add("persist.adb.tcp.port=$port")
        lines.add("service.adb.tcp.port=$port")
        buildProp.writeText(lines.joinToString("\n") + "\n")
    }
}
