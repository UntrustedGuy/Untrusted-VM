package untrusted.vm;

import untrusted.vm.core.VmConfig;
import untrusted.vm.core.VmEngine;

public class VmEngineTest {

    public static void testEngineRootEnabled() {
        VmConfig config = new VmConfig(1, "Rooted VM", "7.1.2", true, 720, 1280, 320, 2048, 4, false, false, 1000L);
        VmEngine engine = new VmEngine(config);

        // id command as non-root
        VmEngine.ExecutionResult nonRootId = engine.executeShell("id", false);
        assert nonRootId.getExitCode() == 0 : "Exit code should be 0";
        assert nonRootId.getOutput().contains("uid=10150(u0_a150)") : "Should report guest user UID";

        // id command requesting root
        VmEngine.ExecutionResult rootId = engine.executeShell("id", true);
        assert rootId.getExitCode() == 0 : "Exit code should be 0";
        assert rootId.getOutput().contains("uid=0(root)") : "Should report root UID 0";
        assert rootId.getOutput().contains("context=u:r:su:s0") : "Should report root SELinux context";

        // which su
        VmEngine.ExecutionResult whichSu = engine.executeShell("which su", false);
        assert whichSu.getExitCode() == 0 : "which su should succeed";
        assert whichSu.getOutput().equals("/system/xbin/su") : "Output should be /system/xbin/su";

        // su -v
        VmEngine.ExecutionResult suV = engine.executeShell("su -v", false);
        assert suV.getExitCode() == 0 : "su -v should succeed";
        assert suV.getOutput().contains("UntrustedVM-su") : "su -v output should contain UntrustedVM-su";

        // getprop
        VmEngine.ExecutionResult tags = engine.executeShell("getprop ro.build.tags", false);
        assert tags.getOutput().equals("test-keys") : "ro.build.tags should be test-keys when rooted";

        VmEngine.ExecutionResult debuggable = engine.executeShell("getprop ro.debuggable", false);
        assert debuggable.getOutput().equals("1") : "ro.debuggable should be 1 when rooted";

        // ls -la /system/xbin
        VmEngine.ExecutionResult lsXbin = engine.executeShell("ls -la /system/xbin", false);
        assert lsXbin.getOutput().contains("su") : "su binary must be listed";
        assert lsXbin.getOutput().contains("daemonsu") : "daemonsu binary must be listed";
    }

    public static void testEngineRootDisabled() {
        VmConfig config = new VmConfig(2, "Unrooted VM", "7.1.2", false, 720, 1280, 320, 2048, 4, false, false, 1000L);
        VmEngine engine = new VmEngine(config);

        // id command requesting root -> should be denied
        VmEngine.ExecutionResult denied = engine.executeShell("id", true);
        assert denied.getExitCode() != 0 : "Root command should fail when root is disabled";
        assert denied.getOutput().contains("Permission denied: Root access is disabled") : "Should mention permission denied";

        // which su -> should fail (not found)
        VmEngine.ExecutionResult whichSu = engine.executeShell("which su", false);
        assert whichSu.getExitCode() != 0 : "which su should fail";
        assert whichSu.getOutput().isEmpty() : "which su output should be empty";

        // su -v -> 127 command not found
        VmEngine.ExecutionResult suV = engine.executeShell("su -v", false);
        assert suV.getExitCode() == 127 : "su -v should return 127";

        // getprop
        VmEngine.ExecutionResult tags = engine.executeShell("getprop ro.build.tags", false);
        assert tags.getOutput().equals("release-keys") : "ro.build.tags should be release-keys when unrooted";

        VmEngine.ExecutionResult debuggable = engine.executeShell("getprop ro.debuggable", false);
        assert debuggable.getOutput().equals("0") : "ro.debuggable should be 0 when unrooted";

        // ls -la /system/xbin
        VmEngine.ExecutionResult lsXbin = engine.executeShell("ls -la /system/xbin", false);
        assert !lsXbin.getOutput().contains("su") : "su binary must NOT be in unrooted xbin";
    }

    public static void testEdgeCases() {
        VmConfig config = new VmConfig(3, "Edge Case VM");
        VmEngine engine = new VmEngine(config);

        assert engine.executeShell(null, false).getOutput().isEmpty();
        assert engine.executeShell("", false).getOutput().isEmpty();
        assert engine.executeShell("    ", false).getOutput().isEmpty();

        VmEngine.ExecutionResult uname = engine.executeShell("uname -a", false);
        assert uname.getExitCode() == 0;
        assert uname.getOutput().contains("Linux");
    }
}
