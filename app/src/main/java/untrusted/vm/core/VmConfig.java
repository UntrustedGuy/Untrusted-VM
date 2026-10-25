package untrusted.vm.core;

import java.util.Objects;
import org.json.JSONObject;

/**
 * Configuration for an Untrusted VM instance.
 * Features per-instance simulated root toggle inspired by VMOS Pro.
 */
public class VmConfig {
    private final int id;
    private final String name;
    private final String androidVersion;
    private final boolean isRootEnabled;
    private final int resolutionWidth;
    private final int resolutionHeight;
    private final int dpi;
    private final int allocatedRamMb;
    private final int cpuCores;
    private final boolean enableGoogleServices;
    private final boolean autoStart;
    private final long createdAt;

    public VmConfig(
        int id,
        String name,
        String androidVersion,
        boolean isRootEnabled,
        int resolutionWidth,
        int resolutionHeight,
        int dpi,
        int allocatedRamMb,
        int cpuCores,
        boolean enableGoogleServices,
        boolean autoStart,
        long createdAt
    ) {
        this.id = id;
        this.name = (name == null || name.trim().isEmpty()) ? "Untrusted VM " + id : name;
        this.androidVersion = (androidVersion == null || androidVersion.trim().isEmpty()) ? "7.1.2" : androidVersion;
        this.isRootEnabled = isRootEnabled;
        this.resolutionWidth = resolutionWidth > 0 ? resolutionWidth : 720;
        this.resolutionHeight = resolutionHeight > 0 ? resolutionHeight : 1280;
        this.dpi = dpi > 0 ? dpi : 320;
        this.allocatedRamMb = allocatedRamMb > 0 ? allocatedRamMb : 2048;
        this.cpuCores = cpuCores > 0 ? cpuCores : 4;
        this.enableGoogleServices = enableGoogleServices;
        this.autoStart = autoStart;
        this.createdAt = createdAt > 0 ? createdAt : System.currentTimeMillis();
    }

    public VmConfig(int id, String name) {
        this(id, name, "7.1.2", false, 720, 1280, 320, 2048, 4, false, false, System.currentTimeMillis());
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getAndroidVersion() { return androidVersion; }
    public boolean isRootEnabled() { return isRootEnabled; }
    public int getResolutionWidth() { return resolutionWidth; }
    public int getResolutionHeight() { return resolutionHeight; }
    public int getDpi() { return dpi; }
    public int getAllocatedRamMb() { return allocatedRamMb; }
    public int getCpuCores() { return cpuCores; }
    public boolean isEnableGoogleServices() { return enableGoogleServices; }
    public boolean isAutoStart() { return autoStart; }
    public long getCreatedAt() { return createdAt; }

    public VmConfig withRootEnabled(boolean newRootState) {
        return new VmConfig(
            id, name, androidVersion, newRootState,
            resolutionWidth, resolutionHeight, dpi,
            allocatedRamMb, cpuCores, enableGoogleServices,
            autoStart, createdAt
        );
    }

    public String toJson() {
        try {
            JSONObject obj = new JSONObject();
            obj.put("id", id);
            obj.put("name", name);
            obj.put("androidVersion", androidVersion);
            obj.put("isRootEnabled", isRootEnabled);
            obj.put("resolutionWidth", resolutionWidth);
            obj.put("resolutionHeight", resolutionHeight);
            obj.put("dpi", dpi);
            obj.put("allocatedRamMb", allocatedRamMb);
            obj.put("cpuCores", cpuCores);
            obj.put("enableGoogleServices", enableGoogleServices);
            obj.put("autoStart", autoStart);
            obj.put("createdAt", createdAt);
            return obj.toString(2);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize VmConfig to JSON", e);
        }
    }

    public static VmConfig fromJson(String jsonStr) {
        try {
            JSONObject obj = new JSONObject(jsonStr);
            return new VmConfig(
                obj.optInt("id", 1),
                obj.optString("name", "Untrusted VM 1"),
                obj.optString("androidVersion", "7.1.2"),
                obj.optBoolean("isRootEnabled", false),
                obj.optInt("resolutionWidth", 720),
                obj.optInt("resolutionHeight", 1280),
                obj.optInt("dpi", 320),
                obj.optInt("allocatedRamMb", 2048),
                obj.optInt("cpuCores", 4),
                obj.optBoolean("enableGoogleServices", false),
                obj.optBoolean("autoStart", false),
                obj.optLong("createdAt", System.currentTimeMillis())
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse VmConfig from JSON", e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VmConfig)) return false;
        VmConfig that = (VmConfig) o;
        return id == that.id &&
               isRootEnabled == that.isRootEnabled &&
               resolutionWidth == that.resolutionWidth &&
               resolutionHeight == that.resolutionHeight &&
               dpi == that.dpi &&
               allocatedRamMb == that.allocatedRamMb &&
               Objects.equals(name, that.name) &&
               Objects.equals(androidVersion, that.androidVersion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, androidVersion, isRootEnabled);
    }
}
