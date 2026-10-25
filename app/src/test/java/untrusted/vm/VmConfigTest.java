package untrusted.vm;

import untrusted.vm.core.VmConfig;

public class VmConfigTest {

    public static void testDefaultConfigValues() {
        VmConfig config = new VmConfig(1, "Untrusted VM 1");
        assert config.getId() == 1 : "Expected ID 1";
        assert "Untrusted VM 1".equals(config.getName()) : "Expected name 'Untrusted VM 1'";
        assert "7.1.2".equals(config.getAndroidVersion()) : "Expected Android 7.1.2";
        assert !config.isRootEnabled() : "Expected root disabled by default";
        assert config.getResolutionWidth() == 720 : "Expected width 720";
        assert config.getResolutionHeight() == 1280 : "Expected height 1280";
        assert config.getDpi() == 320 : "Expected dpi 320";
        assert config.getAllocatedRamMb() == 2048 : "Expected RAM 2048MB";
    }

    public static void testJsonSerializationWithRootEnabled() {
        VmConfig config = new VmConfig(
            2,
            "Rooted Sandbox",
            "9.0",
            true,
            1080,
            1920,
            440,
            4096,
            4,
            true,
            false,
            1000L
        );

        String json = config.toJson();
        assert json.contains("\"isRootEnabled\": true") : "JSON should contain isRootEnabled: true";
        assert json.contains("\"name\": \"Rooted Sandbox\"") : "JSON should contain name";

        VmConfig parsed = VmConfig.fromJson(json);
        assert parsed.getId() == config.getId() : "ID mismatch";
        assert parsed.getName().equals(config.getName()) : "Name mismatch";
        assert parsed.getAndroidVersion().equals(config.getAndroidVersion()) : "Android version mismatch";
        assert parsed.isRootEnabled() : "Expected root to be enabled in parsed config";
        assert parsed.getResolutionWidth() == 1080 : "Width mismatch";
        assert parsed.getResolutionHeight() == 1920 : "Height mismatch";
        assert parsed.getAllocatedRamMb() == 4096 : "RAM mismatch";
    }

    public static void testJsonSerializationWithRootDisabled() {
        VmConfig config = new VmConfig(3, "Clean Stock VM");
        String json = config.toJson();
        assert json.contains("\"isRootEnabled\": false") : "JSON should contain isRootEnabled: false";

        VmConfig parsed = VmConfig.fromJson(json);
        assert !parsed.isRootEnabled() : "Parsed config should have root disabled";
    }

    public static void testWithRootEnabledImmutability() {
        VmConfig original = new VmConfig(4, "Test VM", "12.0", false, 720, 1280, 320, 2048, 4, false, false, 1000L);
        VmConfig modified = original.withRootEnabled(true);

        assert !original.isRootEnabled() : "Original must remain unchanged";
        assert modified.isRootEnabled() : "Modified must have root enabled";
        assert modified.getId() == original.getId() : "ID must match";
        assert modified.getName().equals(original.getName()) : "Name must match";
    }
}
