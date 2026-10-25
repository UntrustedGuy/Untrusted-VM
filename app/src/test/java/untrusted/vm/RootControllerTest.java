package untrusted.vm;

import java.io.File;
import java.nio.file.Files;
import untrusted.vm.core.RootController;

public class RootControllerTest {

    public static void testRootLifecycle() throws Exception {
        File tempDir = Files.createTempDirectory("untrusted_root_test_").toFile();
        try {
            RootController controller = new RootController(1, tempDir);

            // Initially not active
            assert !controller.isRootActive() : "Root should be inactive initially";

            // Enable root
            boolean enabled = controller.setRootState(true);
            assert enabled : "setRootState(true) should return true";
            assert controller.isRootActive() : "Root should be active after enabling";

            File su = controller.getSuBinary();
            assert su.exists() : "su binary must exist";
            assert su.canExecute() : "su binary must be executable";

            File daemonsu = controller.getSuDaemon();
            assert daemonsu.exists() : "daemonsu binary must exist";

            File buildProp = controller.getBuildProp();
            assert buildProp.exists() : "build.prop must exist";
            String propContent = new String(Files.readAllBytes(buildProp.toPath()));
            assert propContent.contains("ro.build.tags=test-keys") : "build.prop should have test-keys";
            assert propContent.contains("ro.debuggable=1") : "build.prop should be debuggable";
            assert propContent.contains("ro.secure=0") : "build.prop should have secure=0";

            // Disable root
            boolean disabled = controller.setRootState(false);
            assert disabled : "setRootState(false) should return true";
            assert !controller.isRootActive() : "Root should be inactive after disabling";
            assert !su.exists() : "su binary must be deleted";
            assert !daemonsu.exists() : "daemonsu binary must be deleted";

            String cleanedProp = new String(Files.readAllBytes(buildProp.toPath()));
            assert cleanedProp.contains("ro.build.tags=release-keys") : "build.prop should have release-keys";
            assert cleanedProp.contains("ro.debuggable=0") : "build.prop should have debuggable=0";
            assert cleanedProp.contains("ro.secure=1") : "build.prop should have secure=1";

        } finally {
            deleteRecursively(tempDir);
        }
    }

    public static void testCommandExecution() throws Exception {
        File tempDir = Files.createTempDirectory("untrusted_cmd_test_").toFile();
        try {
            RootController controller = new RootController(2, tempDir);

            // Root disabled command attempt
            controller.setRootState(false);
            String denied = controller.executeGuestCommand("id", true);
            assert denied.contains("Error: Root is disabled") : "Should deny root command when disabled";

            // Root enabled command
            controller.setRootState(true);
            String rootOut = controller.executeGuestCommand("id", true);
            assert rootOut.contains("uid=0(root)") : "Should report uid 0 when root enabled";

            // Non-root command
            String userOut = controller.executeGuestCommand("whoami", false);
            assert userOut.contains("u0_a100") : "Should report guest user context";

        } finally {
            deleteRecursively(tempDir);
        }
    }

    private static void deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursively(child);
            }
        }
        file.delete();
    }
}
