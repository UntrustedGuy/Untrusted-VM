package untrusted.vm;

import java.io.File;
import java.nio.file.Files;
import untrusted.vm.core.VmFilesystem;

public class VmFilesystemTest {

    public static void testFilesystemLifecycle() throws Exception {
        File tempDir = Files.createTempDirectory("untrusted_fs_test_").toFile();
        try {
            VmFilesystem fs = new VmFilesystem(1, tempDir);

            boolean inited = fs.initializeSkeleton();
            assert inited : "initializeSkeleton should succeed";

            assert fs.getRootfsDir().exists() : "rootfsDir should exist";
            assert fs.getSystemDir().exists() : "systemDir should exist";
            assert fs.getDataDir().exists() : "dataDir should exist";
            assert fs.getSdcardDir().exists() : "sdcardDir should exist";
            assert new File(fs.getSystemDir(), "bin").exists() : "system/bin should exist";
            assert new File(fs.getSystemDir(), "xbin").exists() : "system/xbin should exist";
            assert new File(fs.getDataDir(), "app").exists() : "data/app should exist";
            assert new File(fs.getDataDir(), "data").exists() : "data/data should exist";

            // Add dummy files
            File dummySys = new File(fs.getSystemDir(), "build.prop");
            Files.write(dummySys.toPath(), "test_prop=1".getBytes());

            File dummyData = new File(fs.getDataDir(), "app/test.apk");
            Files.write(dummyData.toPath(), "test_apk".getBytes());

            assert dummySys.exists() : "dummySys should exist";
            assert dummyData.exists() : "dummyData should exist";

            // Wipe data
            boolean wiped = fs.wipeData();
            assert wiped : "wipeData should succeed";
            assert dummySys.exists() : "system file must remain after wipeData";
            assert !dummyData.exists() : "data file must be removed after wipeData";
            assert fs.getDataDir().exists() : "dataDir should be recreated";

            // Full deletion
            boolean deleted = fs.deleteInstanceFiles();
            assert deleted : "deleteInstanceFiles should succeed";
            assert !tempDir.exists() : "rootDir should be deleted";

        } finally {
            if (tempDir.exists()) {
                deleteRecursively(tempDir);
            }
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
