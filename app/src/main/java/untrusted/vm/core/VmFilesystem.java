package untrusted.vm.core;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * Manages the isolated virtual filesystem directory for a VM instance.
 * Located at: <filesDir>/instances/vm_<id>/
 */
public class VmFilesystem {

    private final int vmId;
    private final File rootDir;
    private final File rootfsDir;
    private final File systemDir;
    private final File dataDir;
    private final File sdcardDir;
    private final File snapshotsDir;
    private final File logsDir;

    public VmFilesystem(int vmId, File rootDir) {
        this.vmId = vmId;
        this.rootDir = rootDir;
        this.rootfsDir = new File(rootDir, "rootfs");
        this.systemDir = new File(rootfsDir, "system");
        this.dataDir = new File(rootfsDir, "data");
        this.sdcardDir = new File(rootfsDir, "sdcard");
        this.snapshotsDir = new File(rootDir, "snapshots");
        this.logsDir = new File(rootDir, "logs");
    }

    public int getVmId() { return vmId; }
    public File getRootDir() { return rootDir; }
    public File getRootfsDir() { return rootfsDir; }
    public File getSystemDir() { return systemDir; }
    public File getDataDir() { return dataDir; }
    public File getSdcardDir() { return sdcardDir; }
    public File getSnapshotsDir() { return snapshotsDir; }
    public File getLogsDir() { return logsDir; }

    public boolean initializeSkeleton() {
        try {
            List<File> dirs = Arrays.asList(
                rootDir,
                rootfsDir,
                new File(systemDir, "bin"),
                new File(systemDir, "xbin"),
                new File(systemDir, "etc"),
                new File(systemDir, "lib"),
                new File(dataDir, "app"),
                new File(dataDir, "data"),
                new File(dataDir, "system"),
                sdcardDir,
                snapshotsDir,
                logsDir,
                new File(rootfsDir, "dev"),
                new File(rootfsDir, "proc"),
                new File(rootfsDir, "sys")
            );
            for (File dir : dirs) {
                if (!dir.exists()) {
                    dir.mkdirs();
                }
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public long calculateTotalSizeBytes() {
        return calculateDirSize(rootDir);
    }

    private long calculateDirSize(File dir) {
        if (!dir.exists()) return 0L;
        long size = 0L;
        File[] files = dir.listFiles();
        if (files == null) return 0L;
        for (File f : files) {
            size += f.isDirectory() ? calculateDirSize(f) : f.length();
        }
        return size;
    }

    public boolean wipeData() {
        try {
            if (dataDir.exists()) {
                deleteRecursively(dataDir);
                dataDir.mkdirs();
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean deleteInstanceFiles() {
        try {
            if (rootDir.exists()) {
                return deleteRecursively(rootDir);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        return file.delete();
    }
}
