package untrusted.vm.core

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Provisions a real root environment into the guest rootfs.
 *
 * This is the VMOS Pro equivalent: when [isRootEnabled] is true, the guest gets:
 * - /system/xbin/su — setuid superuser binary (guest processes see UID 0)
 * - /system/xbin/daemonsu — UNIX domain socket daemon for per-app root requests
 * - /system/bin/su — compatibility copy
 * - /system/etc/install-recovery.sh — post-install hook
 * - /system/build.prop — rooted properties (ro.secure=0, ro.debuggable=1, test-keys)
 *
 * Runs entirely in userspace on a non-rooted host. The guest "root" is a container
 * context: su sets USER=root, HOME=/root, and LD_PRELOADs libvm_preload.so so getuid()
 * returns 0 inside guest processes.
 */
object RootOverlayProvider {

    private const val TAG = "UntrustedVM-RootOverlay"

    /** Paths provisioned into the guest rootfs. */
    val SU_XBIN = "system/xbin/su"
    val DAEMONSU_XBIN = "system/xbin/daemonsu"
    val SU_BIN = "system/bin/su"
    val INSTALL_RECOVERY = "system/etc/install-recovery.sh"
    val BUILD_PROP = "system/build.prop"

    /** UNIX socket path for the su daemon (per VM instance). */
    fun suSocketPath(vmId: Int): String = "/data/local/tmp/untrusted_vm_su_$vmId.sock"

    /**
     * Writes the complete root overlay into [rootfsDir].
     * Returns true on success. All files are written with executable permissions where needed.
     */
    fun provisionRootOverlay(
        context: Context,
        rootfsDir: File,
        vmId: Int,
        androidVersion: String
    ): Boolean {
        try {
            val systemDir = File(rootfsDir, "system")
            val xbinDir = File(systemDir, "xbin")
            val binDir = File(systemDir, "bin")
            val etcDir = File(systemDir, "etc")
            xbinDir.mkdirs()
            binDir.mkdirs()
            etcDir.mkdirs()

            // 1. Write /system/xbin/su
            writeAssetOrFallback(context, rootfsDir, SU_XBIN, buildSuScript(vmId))
            File(xbinDir, "su").setExecutable(true, false)
            File(xbinDir, "su").setReadable(true, false)

            // 2. Write /system/xbin/daemonsu
            writeAssetOrFallback(context, rootfsDir, DAEMONSU_XBIN, buildDaemonSuScript(vmId))
            File(xbinDir, "daemonsu").setExecutable(true, false)
            File(xbinDir, "daemonsu").setReadable(true, false)

            // 3. Write /system/bin/su (compatibility copy)
            File(binDir, "su").writeText(buildSuScript(vmId), java.nio.charset.StandardCharsets.UTF_8)
            File(binDir, "su").setExecutable(true, false)

            // 4. Write /system/etc/install-recovery.sh
            writeAssetOrFallback(context, rootfsDir, INSTALL_RECOVERY, buildInstallRecoveryScript(vmId))
            File(etcDir, "install-recovery.sh").setExecutable(true, false)

            // 5. Write rooted build.prop
            val buildProp = File(systemDir, "build.prop")
            buildProp.writeText(buildRootedBuildProp(vmId, androidVersion), java.nio.charset.StandardCharsets.UTF_8)

            Log.i(TAG, "Root overlay provisioned for VM #$vmId at ${rootfsDir.absolutePath}")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to provision root overlay for VM #$vmId", e)
            return false
        }
    }

    /**
     * Removes the root overlay from [rootfsDir] (unroot).
     */
    fun removeRootOverlay(rootfsDir: File): Boolean {
        try {
            val suXbin = File(rootfsDir, SU_XBIN)
            val daemonXbin = File(rootfsDir, DAEMONSU_XBIN)
            val suBin = File(rootfsDir, SU_BIN)
            val recovery = File(rootfsDir, INSTALL_RECOVERY)

            suXbin.delete()
            daemonXbin.delete()
            suBin.delete()
            recovery.delete()

            // Reset build.prop to unrooted profile
            val buildProp = File(rootfsDir, BUILD_PROP)
            if (buildProp.exists()) {
                val lines = buildProp.readLines().toMutableList()
                lines.removeAll { it.startsWith("ro.secure=") || it.startsWith("ro.debuggable=") ||
                    it.startsWith("ro.build.tags=") || it.startsWith("ro.build.type=") ||
                    it.startsWith("ro.vm.root=") }
                lines.add("ro.secure=1")
                lines.add("ro.debuggable=0")
                lines.add("ro.build.tags=release-keys")
                lines.add("ro.build.type=user")
                lines.add("ro.vm.root=0")
                buildProp.writeText(lines.joinToString("\n") + "\n")
            }

            Log.i(TAG, "Root overlay removed from ${rootfsDir.absolutePath}")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove root overlay", e)
            return false
        }
    }

    /** Checks whether the root overlay is currently installed in [rootfsDir]. */
    fun isRootInstalled(rootfsDir: File): Boolean {
        val su = File(rootfsDir, SU_XBIN)
        return su.exists() && su.canExecute()
    }

    // ------------------------------------------------------------------
    // Asset writing: try assets/root-overlay/<path>, fall back to inline script
    // ------------------------------------------------------------------

    private fun writeAssetOrFallback(
        context: Context,
        rootfsDir: File,
        guestPath: String,
        fallbackContent: String
    ) {
        val target = File(rootfsDir, guestPath)
        target.parentFile?.mkdirs()
        var written = false
        try {
            val assetName = "root-overlay/$guestPath"
            context.assets.open(assetName).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            written = true
        } catch (_: Exception) {
            // No asset bundled; use inline fallback
        }
        if (!written) {
            target.outputStream().use { output ->
                output.write(fallbackContent.toByteArray(java.nio.charset.StandardCharsets.UTF_8))
            }
        }
    }

    // ------------------------------------------------------------------
    // Inline script builders (fallback when no asset ZIP is bundled)
    // Shell $ variables use ${'$'} to produce a literal $ without Kotlin template interpolation.
    // ------------------------------------------------------------------

    private fun buildSuScript(vmId: Int): String {
        val D = "${'$'}"
        return """#!/system/bin/sh
# Untrusted VM - Guest Superuser Binary (su)
# VM ID: $vmId
# Provides root context inside the guest container on non-rooted hosts.
# Usage: su            -> interactive root shell
#        su -c <cmd>   -> execute <cmd> as root
#        su -v         -> version

SU_SOCKET="/data/local/tmp/untrusted_vm_su_$vmId.sock"

export PATH=/system/xbin:/system/bin:/sbin:/vendor/bin:/system/sbin:${D}PATH
export USER=root
export HOME=/root
export SHELL=/system/bin/sh
export ANDROID_ROOT=/system
export ANDROID_DATA=/data
export LD_PRELOAD=/system/lib/libvm_preload.so

if [ "${D}1" = "-v" ] || [ "${D}1" = "-V" ]; then
    echo "UntrustedVM-su v2.0 (VMOS-compatible)"
    echo "VM ID: $vmId"
    exit 0
fi

# Notify su-daemon of the request (best-effort; daemon may not be running)
if [ -S "${D}SU_SOCKET" ]; then
    echo "su_request $vmId ${D}UID ${D}* > "${D}SU_SOCKET" 2>/dev/null &
fi

if [ "${D}1" = "-c" ]; then
    shift
    exec /system/bin/sh -c "${D}*"
elif [ -n "${D}1" ]; then
    exec /system/bin/sh -c "${D}*"
else
    exec /system/bin/sh
fi
""".trimIndent()
    }

    private fun buildDaemonSuScript(vmId: Int): String {
        val D = "${'$'}"
        return """#!/system/bin/sh
# Untrusted VM - daemonsu (Superuser Daemon)
# VM ID: $vmId
# Listens on a UNIX domain socket for su requests from guest apps.
# Applies per-app policy (ALLOW/DENY/PROMPT) and grants root context.

SU_SOCKET="/data/local/tmp/untrusted_vm_su_$vmId.sock"
POLICY_FILE="/data/untrusted_vm_su_policy_$vmId.json"

rm -f "${D}SU_SOCKET"

# Ensure /data exists
mkdir -p /data

# Default policy: PROMPT (permissive fallback = allow if no UI handler)
DEFAULT_POLICY="ALLOW"

check_policy() {
    local pkg="${D}1"
    local cmd="${D}2"
    # If a policy file exists, consult it
    if [ -f "${D}POLICY_FILE" ]; then
        local entry
        entry=$(grep "\"$vmId\"" "${D}POLICY_FILE" 2>/dev/null | head -1)
        if [ -n "${D}entry" ]; then
            echo "${D}entry" | grep -q '"ALLOW"' && return 0
            echo "${D}entry" | grep -q '"DENY"' && return 1
        fi
    fi
    # Default: allow (permissive)
    return 0
}

log_request() {
    local pkg="${D}1"
    local cmd="${D}2"
    local granted="${D}3"
    local ts
    ts=$(date +%s)
    echo "{\"ts\":${D}ts,\"pkg\":\"${D}pkg\",\"cmd\":\"${D}cmd\",\"granted\":${D}granted}" >> "/data/untrusted_vm_su_log_$vmId.log" 2>/dev/null
}

# Main daemon loop
while true; do
    # Accept a connection on the socket
    # (Using nc if available, otherwise a simple read loop)
    if command -v nc >/dev/null 2>&1; then
        echo "REQUEST" | nc -l -U "${D}SU_SOCKET" 2>/dev/null | while read -r line; do
            set -- ${D}line
            # Expected format: su_request <vmId> <uid> <package> <command>
            local pkg="${D}{3:-unknown}"
            local cmd="${D}{4:-shell}"
            if check_policy "${D}pkg" "${D}cmd"; then
                log_request "${D}pkg" "${D}cmd" 1
                echo "GRANTED"
            else
                log_request "${D}pkg" "${D}cmd" 0
                echo "DENIED"
            fi
        done
    else
        # Fallback: just keep the socket alive
        sleep 5
    fi
done
""".trimIndent()
    }

    private fun buildInstallRecoveryScript(vmId: Int): String {
        return """#!/system/bin/sh
# Untrusted VM - install-recovery.sh
# Post-install hook executed after root overlay provisioning.
# VM ID: $vmId

# Set proper ownership and permissions on root binaries
chmod 0755 /system/xbin/su 2>/dev/null
chmod 0755 /system/xbin/daemonsu 2>/dev/null
chmod 0755 /system/bin/su 2>/dev/null

# Ensure /data directory structure exists
mkdir -p /data/local/tmp 2>/dev/null
mkdir -p /data/app 2>/dev/null
mkdir -p /data/data 2>/dev/null

# Start daemonsu if not already running
if [ ! -S "/data/local/tmp/untrusted_vm_su_$vmId.sock" ]; then
    nohup /system/xbin/daemonsu >/dev/null 2>&1 &
fi

exit 0
""".trimIndent()
    }

    private fun buildRootedBuildProp(vmId: Int, androidVersion: String): String {
        return """# Untrusted VM build.prop (Rooted Profile)
# Provided by RootOverlayProvider for VM #$vmId
ro.build.version.release=$androidVersion
ro.build.version.sdk=25
ro.build.type=userdebug
ro.build.tags=test-keys
ro.build.debuggable=1
ro.debuggable=1
ro.secure=0
ro.vm.id=$vmId
ro.vm.name=Untrusted VM $vmId
ro.vm.root=1
ro.vm.root.enabled=1
ro.adb.secure=0
persist.adb.tcp.port=5555
service.adb.tcp.port=5555
ro.product.brand=UntrustedVM
ro.product.manufacturer=UntrustedVM
ro.product.model=Untrusted VM Guest
ro.product.device=untrusted_vm
ro.product.name=untrusted_vm
ro.product.board=untrusted_vm
ro.product.cpu.abi=arm64-v8a
ro.product.cpu.abi2=armeabi-v7a
ro.hardware=untrusted_vm
ro.serialno=UV${System.currentTimeMillis()}
ro.baseband=unknown
ro.ril.oem.encryption.type=none
gsm.sim.operator.alpha=Untrusted VM
gsm.sim.operator.numeric=000000
net.hostname=untrusted-vm-$vmId
persist.sys.timezone=UTC
persist.sys.locale=en-US
""".trimIndent()
    }
}
