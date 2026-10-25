package untrusted.vm.core;

/**
 * Execution engine for guest commands and container environment emulation.
 * Simulates guest Android shell behaviors and handles UID virtualization.
 */
public class VmEngine {

    public static class ExecutionResult {
        private final int exitCode;
        private final String output;

        public ExecutionResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }

        public int getExitCode() { return exitCode; }
        public String getOutput() { return output; }
    }

    private final VmConfig config;

    public VmEngine(VmConfig config) {
        this.config = config;
    }

    public ExecutionResult executeShell(String command, boolean requestRoot) {
        if (command == null || command.trim().isEmpty()) {
            return new ExecutionResult(0, "");
        }
        String trimmed = command.trim();
        boolean isRoot = requestRoot && config.isRootEnabled();

        if (requestRoot && !config.isRootEnabled()) {
            return new ExecutionResult(
                1,
                "Permission denied: Root access is disabled for this VM instance. Enable 'Simulated Root Access' in VM Settings."
            );
        }

        if (trimmed.equals("id")) {
            if (isRoot) {
                return new ExecutionResult(0, "uid=0(root) gid=0(root) groups=0(root),1004(input),1007(log),1011(adb),1015(sdcard_rw),1028(sdcard_r),3001(net_bt_admin),3002(net_bt),3003(inet),3006(net_bw_stats) context=u:r:su:s0");
            } else {
                return new ExecutionResult(0, "uid=10150(u0_a150) gid=10150(u0_a150) groups=10150(u0_a150),3003(inet),9997(everybody) context=u:r:untrusted_app:s0:c150,c256,c512,c768");
            }
        }

        if (trimmed.startsWith("which su")) {
            if (config.isRootEnabled()) {
                return new ExecutionResult(0, "/system/xbin/su");
            } else {
                return new ExecutionResult(1, "");
            }
        }

        if (trimmed.equals("su -v") || trimmed.equals("su -V")) {
            if (config.isRootEnabled()) {
                return new ExecutionResult(0, "UntrustedVM-su v1.0 (VMOS-compatible)");
            } else {
                return new ExecutionResult(127, "/system/bin/sh: su: not found");
            }
        }

        if (trimmed.startsWith("su")) {
            if (config.isRootEnabled()) {
                String sub = trimmed.substring(2).trim();
                if (sub.startsWith("-c ")) {
                    String realCmd = sub.substring(3).trim();
                    if ((realCmd.startsWith("\"") && realCmd.endsWith("\"")) ||
                        (realCmd.startsWith("'") && realCmd.endsWith("'"))) {
                        realCmd = realCmd.substring(1, realCmd.length() - 1);
                    }
                    return executeShell(realCmd, true);
                } else if (sub.isEmpty()) {
                    return new ExecutionResult(0, "Interactive root shell granted (UID 0).\nType 'exit' to return.");
                } else {
                    return executeShell(sub, true);
                }
            } else {
                return new ExecutionResult(127, "/system/bin/sh: su: inaccessible or not found");
            }
        }

        if (trimmed.startsWith("getprop")) {
            String propName = trimmed.substring(7).trim();
            if (propName.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                sb.append("[ro.build.version.release]: [").append(config.getAndroidVersion()).append("]\n");
                sb.append("[ro.build.type]: [").append(config.isRootEnabled() ? "userdebug" : "user").append("]\n");
                sb.append("[ro.build.tags]: [").append(config.isRootEnabled() ? "test-keys" : "release-keys").append("]\n");
                sb.append("[ro.debuggable]: [").append(config.isRootEnabled() ? "1" : "0").append("]\n");
                sb.append("[ro.secure]: [").append(config.isRootEnabled() ? "0" : "1").append("]\n");
                sb.append("[ro.vm.id]: [").append(config.getId()).append("]\n");
                sb.append("[ro.vm.name]: [").append(config.getName()).append("]\n");
                sb.append("[ro.vm.root.enabled]: [").append(config.isRootEnabled() ? "1" : "0").append("]");
                return new ExecutionResult(0, sb.toString());
            } else {
                String val = "";
                switch (propName) {
                    case "ro.build.version.release": val = config.getAndroidVersion(); break;
                    case "ro.build.type": val = config.isRootEnabled() ? "userdebug" : "user"; break;
                    case "ro.build.tags": val = config.isRootEnabled() ? "test-keys" : "release-keys"; break;
                    case "ro.debuggable": val = config.isRootEnabled() ? "1" : "0"; break;
                    case "ro.secure": val = config.isRootEnabled() ? "0" : "1"; break;
                    case "ro.vm.id": val = String.valueOf(config.getId()); break;
                    case "ro.vm.name": val = config.getName(); break;
                    case "ro.vm.root.enabled": val = config.isRootEnabled() ? "1" : "0"; break;
                    default: val = ""; break;
                }
                return new ExecutionResult(0, val);
            }
        }

        if (trimmed.equals("uname -a")) {
            return new ExecutionResult(0, "Linux localhost 5.15.0-untrusted-vm #1 SMP PREEMPT aarch64 Android");
        }

        if (trimmed.equals("ls -la /system/xbin")) {
            if (config.isRootEnabled()) {
                return new ExecutionResult(0, "-rwxr-xr-x 1 root root  1248 2026-10-25 00:00 daemonsu\n-rwxr-xr-x 1 root root  1248 2026-10-25 00:00 su");
            } else {
                return new ExecutionResult(0, "total 0");
            }
        }

        String prefix = isRoot ? "root@untrusted-vm:/#" : "u0_a150@untrusted-vm:/$";
        return new ExecutionResult(0, prefix + " " + trimmed + "\n[Command executed successfully]");
    }
}
