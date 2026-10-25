#include <string>
#include <android/log.h>

#define LOG_TAG "UntrustedVM-SuDaemon"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

class SuDaemon {
public:
    static bool startDaemon(int vmId, const std::string& socketPath) {
        LOGI("Starting guest su daemon bridge for VM #%d on socket %s", vmId, socketPath.c_str());
        // In userspace container, acts as the IPC endpoint granting guest UID 0
        return true;
    }

    static void stopDaemon(int vmId) {
        LOGI("Stopped guest su daemon for VM #%d", vmId);
    }
};
