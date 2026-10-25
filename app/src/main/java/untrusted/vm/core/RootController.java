package untrusted.vm.core;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Controller responsible for per-instance simulated root management.
 * Mirrors VMOS Pro's root toggle mechanism:
 * - Operates entirely in userspace without requiring host root or Shizuku.
 * - Injects or removes guest `su` binaries and su daemon bridges in the instance rootfs.
 * - Alters guest system properties (ro.build.tags, ro.debuggable, ro.secure).
 */
public class RootController {

    private final int vmId;
    private final File vmRootDir;

    public RootController(int vmId, File vmRootDir) {
        this.vmId = vmId;
        this.vmRootDir = vmRootDir;
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
        return su.exists() && (su.length() > 0 || su.canExecute());
    }

    /**
     * Applies the requested root state for this instance.
     * @param enable If true, provisions simulated root binaries and test-keys build.prop.
     *               If false, cleans up su binaries and sets secure build properties.
     */
    public synchronized boolean setRootState(boolean enable) {
        return enable ? installGuestRoot() : removeGuestRoot();
    }

    private boolean installGuestRoot() {
        try {
            File xbin = getXbinDir();
            if (!xbin.exists()) {
                xbin.mkdirs();
            }
            File bin = getBinDir();
            if (!bin.exists()) {
                bin.mkdirs();
            }

            String suScript =
                "#!/system/bin/sh\n" +
                "# Untrusted VM - Simulated Guest Superuser Binary\n" +
                "# Host UID: non-root unprivileged container\n" +
                "# Guest UID: 0 (root)\n\n" +
                "export PATH=/system/xbin:/system/bin:/sbin:/vendor/bin:$PATH\n" +
                "export USER=root\n" +
                "export HOME=/root\n" +
                "export SHELL=/system/bin/sh\n\n" +
                "if [ \"$1\" = \"-v\" ] || [ \"$1\" = \"-V\" ]; then\n" +
                "    echo \"UntrustedVM-su v1.0 (VMOS-compatible)\"\n" +
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
            su.setExecutable(true, false);
            su.setReadable(true, false);

            File daemonsu = getSuDaemon();
            try (FileOutputStream fos = new FileOutputStream(daemonsu)) {
                fos.write(suScript.getBytes(StandardCharsets.UTF_8));
            }
            daemonsu.setExecutable(true, false);
            daemonsu.setReadable(true, false);

            updateBuildProp(true);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean removeGuestRoot() {
        try {
            File su = getSuBinary();
            if (su.exists()) {
                su.delete();
            }
            File daemonsu = getSuDaemon();
            if (daemonsu.exists()) {
                daemonsu.delete();
            }
            updateBuildProp(false);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private void updateBuildProp(boolean rooted) {
        File system = getSystemDir();
        if (!system.exists()) {
            system.mkdirs();
        }
        String content = rooted ?
            "# Untrusted VM build.prop (Rooted Profile)\n" +
            "ro.build.type=userdebug\n" +
            "ro.build.tags=test-keys\n" +
            "ro.debuggable=1\n" +
            "ro.secure=0\n" +
            "ro.vm.id=" + vmId + "\n" +
            "ro.vm.root=1\n" :
            "# Untrusted VM build.prop (Clean Profile)\n" +
            "ro.build.type=user\n" +
            "ro.build.tags=release-keys\n" +
            "ro.debuggable=0\n" +
            "ro.secure=1\n" +
            "ro.vm.id=" + vmId + "\n" +
            "ro.vm.root=0\n";

        try (FileOutputStream fos = new FileOutputStream(getBuildProp())) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            e.printStackTrace();
        }
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
