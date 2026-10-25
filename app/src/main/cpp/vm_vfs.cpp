#include <string>
#include <cstring>
#include <android/log.h>

#define LOG_TAG "UntrustedVM-VFS"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

class VirtualFilesystem {
public:
    static bool redirectPath(
        const std::string& guestPath,
        const std::string& rootfsBase,
        bool isRootEnabled,
        std::string& outHostPath
    ) {
        // Enforce per-instance root visibility
        if (guestPath == "/system/xbin/su" || guestPath == "/system/bin/su") {
            if (!isRootEnabled) {
                // Deny / mask su when root is disabled for this instance
                outHostPath = "";
                return false;
            }
        }

        if (guestPath.rfind("/system", 0) == 0) {
            outHostPath = rootfsBase + guestPath;
            return true;
        } else if (guestPath.rfind("/data", 0) == 0) {
            outHostPath = rootfsBase + guestPath;
            return true;
        } else if (guestPath.rfind("/sdcard", 0) == 0) {
            outHostPath = rootfsBase + "/sdcard" + guestPath.substr(7);
            return true;
        }

        outHostPath = guestPath;
        return true;
    }
};
