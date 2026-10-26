package untrusted.vm.core;

import android.content.Context;
import android.util.Log;
import untrusted.vm.core.RootOverlayProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Controller responsible for per-instance real root provisioning in the guest rootfs.
 * Mirrors VMOS Pro's root toggle mechanism:
 * - Provisions /system/xbin/su, /system/xbin/daemonsu, /system/bin/su into the guest rootfs.
 * - Updates guest build.prop with rooted properties (ro.secure=0, ro.debuggable=1, test-keys).
 * - Starts daemonsu UNIX socket daemon for per-app root requests.
 * - Runs entirely in userspace on a non-rooted host.
 */
public class RootController {

    private static final String TAG = "UntrustedVM-RootController";

    private final int vmId;
    private final File vmRootDir;
    private final Context context;

    public RootController(int vmId, File vmRootDir) {
        this(vmId, vmRootDir, null);
    }

    public RootController(int vmId, File vmRootDir, Context context) {
        this.vmId = vmId;
        this.vmRootDir = vmRootDir;
        this.context = context;
    }

    public File getSystemDir() {
        return new File(vmRootDir, "rootfs/system");
    }

    public File getXbinDir() {
        return new File(getSystemDir(), "xbin");
    }

    public File getBinDir() {
        return new File(getSystemDir(), "bin");
    }

    public File getSuBinary() {
        return new File(getXbinDir(), "su");
    }

    public File getSuDaemon() {
        return new File(getXbinDir(), "daemonsu");
    }

    public File getBuildProp() {
        return new File(getSystemDir(), "build.prop");
    }

    /**
     * Checks if simulated root is currently active in this instance's filesystem.
     */
    public boolean isRootActive() {
        File su = getSuBinary();
        return su.exists() && su.canExecute();
    }

    /**
     * Applies the requested root state for this instance.
     * @param enable If true, provisions real su + daemonsu binaries and rooted build.prop.
     *               If false, cleans up su binaries and sets secure build properties.
     */
    public synchronized boolean setRootState(boolean enable) {
        return enable ? installGuestRoot() : removeGuestRoot();
    }

    private boolean installGuestRoot() {
        try {
            File rootfsDir = new File(vmRootDir, "rootfs");

            if (context != null) {
                // Provision the full root overlay (su, daemonsu, build.prop, etc.)
                boolean ok = RootOverlayProvider.provisionRootOverlay(
                        context,
                        rootfsDir,
                        vmId,
                        getAndroidVersion()
                );
                if (!ok) {
                    return false;
                }
            } else {
                // No Context available (e.g. unit tests): fall back to inline
                // script-based su binaries so the guest still has a working su.
                installInlineGuestRoot(rootfsDir);
            }

            // Ensure the su binary is executable
            File su = getSuBinary();
            su.setExecutable(true, false);
            su.setReadable(true, false);

            File daemon = getSuDaemon();
            daemon.setExecutable(true, false);
            daemon.setReadable(true, false);

            Log.i(TAG, "Guest root installed for VM #" + vmId);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to install guest root for VM #" + vmId, e);
            return false;
        }
    }

    /**
     * Inline fallback that writes shell-script su/daemonsu binaries into the guest rootfs.
     * Used when no Context is available to load root-overlay assets (e.g. unit tests).
     */
    private void installInlineGuestRoot(File rootfsDir) throws Exception {
        File systemDir = File(rootfsDir, "system");
        File xbinDir = File(systemDir, "xbin");
        File binDir = File(systemDir, "bin");
        xbinDir.mkdirs();
        binDir.mkdirs();

        String suScript =
            "#!/system/bin/sh\n" +
            "# Untrusted VM - Simulated Guest Superuser Binary\n" +
            "export PATH=/system/xbin:/system/bin:/sbin:/vendor/bin:$PATH\n" +
            "export USER=root\n" +
            "export HOME=/root\n" +
            "export SHELL=/system/bin/sh\n\n" +
            "if [ \"$1\" = \"-v\" ] || [ \"$1\" = \"-V\" ]; then\n" +
            "    echo \"UntrustedVM-su v1.0\"\n" +
            "    exit 0\n" +
            "fi\n\n" +
            "if [ \"$1\" = \"-c\" ]; then\n" +
            "    shift\n" +
            "    exec /system/bin/sh -c \"$@\"\n" +
            "fi\n\n" +
            "if [ -n \"$1\" ]; then\n" +
            "    exec /system/bin/sh -c \"$*\"\n" +
            "else\n" +
            "    exec /system/bin/sh\n" +
            "fi\n";

        File su = getSuBinary();
        try (FileOutputStream fos = new FileOutputStream(su)) {
            fos.write(suScript.getBytes(StandardCharsets.UTF_8));
        }

        File daemonsu = getSuDaemon();
        try (FileOutputStream fos = new FileOutputStream(daemonsu)) {
            fos.write(suScript.getBytes(StandardCharsets.UTF_8));
        }

        File binSu = new File(binDir, "su");
        try (FileOutputStream fos = new FileOutputStream(binSu)) {
            fos.write(suScript.getBytes(StandardCharsets.UTF_8));
        }

        updateBuildProp(true);
    }

    private boolean removeGuestRoot() {
        try {
            File rootfsDir = new File(vmRootDir, "rootfs");
            boolean ok = RootOverlayProvider.removeRootOverlay(rootfsDir);
            if (!ok) {
                return false;
            }
            Log.i(TAG, "Guest root removed for VM #" + vmId);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to remove guest root for VM #" + vmId, e);
            return false;
        }
    }

    private String getAndroidVersion() {
        // Try to read from existing build.prop
        File buildProp = getBuildProp();
        if (buildProp.exists()) {
            try {
                java.util.List<String> lines = java.nio.file.Files.readAllLines(buildProp.toPath());
                for (String line : lines) {
                    if (line.startsWith("ro.build.version.release=")) {
                        return line.substring("ro.build.version.release=".length()).trim();
                    }
                }
            } catch (Exception ignored) {}
        }
        return "7.1.2";
    }

    public String executeGuestCommand(String command, boolean asRoot) {
        if (asRoot && !isRootActive()) {
            return "Error: Root is disabled for VM instance " + vmId + ". Please enable Root in VM Settings.";
        }
        VmConfig tempConfig = new VmConfig(vmId, "Untrusted VM " + vmId, "7.1.2", isRootActive(), 720, 1280, 320, 2048, 4, false, false, 0);
        VmEngine engine = new VmEngine(tempConfig);
        VmEngine.ExecutionResult res = engine.executeShell(command, asRoot);
        return res.getOutput();
    }
}
