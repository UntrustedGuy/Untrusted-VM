#include "vm_vfs.h"
#include <android/log.h>
#include <unistd.h>
#include <fcntl.h>
#include <cerrno>

#define LOG_TAG "UntrustedVM-VFS"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

namespace untrusted {
namespace vm {

VirtualFilesystem& VirtualFilesystem::getInstance() {
    static VirtualFilesystem instance;
    return instance;
}

void VirtualFilesystem::registerVmRootfs(int vmId, const std::string& rootfsPath, bool rootEnabled) {
    std::lock_guard<std::mutex> lock(mutex_);
    VmFsConfig config;
    config.rootfsPath = rootfsPath;
    config.rootEnabled = rootEnabled;
    vmConfigs_[vmId] = config;
    LOGI("Registered rootfs for VM #%d -> %s (root: %s)",
         vmId, rootfsPath.c_str(), rootEnabled ? "ON" : "OFF");
}

void VirtualFilesystem::updateRootOption(int vmId, bool rootEnabled) {
    std::lock_guard<std::mutex> lock(mutex_);
    auto it = vmConfigs_.find(vmId);
    if (it != vmConfigs_.end()) {
        it->second.rootEnabled = rootEnabled;
        LOGI("Updated root option for VM #%d -> %s", vmId, rootEnabled ? "ON" : "OFF");
    }
}

void VirtualFilesystem::unregisterVm(int vmId) {
    std::lock_guard<std::mutex> lock(mutex_);
    vmConfigs_.erase(vmId);
    LOGI("Unregistered VM #%d from VFS", vmId);
}

bool VirtualFilesystem::translatePath(int vmId, const std::string& guestPath, std::string& outHostPath) {
    std::lock_guard<std::mutex> lock(mutex_);
    auto it = vmConfigs_.find(vmId);
    if (it == vmConfigs_.end()) {
        outHostPath = guestPath;
        return false;
    }

    const VmFsConfig& cfg = it->second;

    // 1. Root binary visibility check
    if (guestPath == "/system/xbin/su" || guestPath == "/system/bin/su" ||
        guestPath == "/sbin/su" || guestPath == "/su/bin/su") {
        if (!cfg.rootEnabled) {
            // Mask su completely when root is disabled for this instance
            outHostPath = "";
            return false;
        }
        outHostPath = cfg.rootfsPath + "/system/xbin/su";
        return true;
    }

    // 2. Virtual SELinux enforce file
    if (guestPath == "/sys/fs/selinux/enforce") {
        outHostPath = cfg.rootfsPath + "/sys/fs/selinux/enforce";
        return true;
    }

    // 3. System partitions redirected to sandboxed rootfs
    if (guestPath.rfind("/system", 0) == 0) {
        outHostPath = cfg.rootfsPath + guestPath;
        return true;
    } else if (guestPath.rfind("/vendor", 0) == 0) {
        outHostPath = cfg.rootfsPath + guestPath;
        return true;
    } else if (guestPath.rfind("/data", 0) == 0) {
        outHostPath = cfg.rootfsPath + guestPath;
        return true;
    } else if (guestPath.rfind("/sdcard", 0) == 0) {
        outHostPath = cfg.rootfsPath + "/sdcard" + guestPath.substr(7);
        return true;
    } else if (guestPath.rfind("/storage", 0) == 0) {
        outHostPath = cfg.rootfsPath + guestPath;
        return true;
    } else if (guestPath.rfind("/dev/socket", 0) == 0) {
        outHostPath = cfg.rootfsPath + guestPath;
        return true;
    }

    // Fallback: pass through to host path (e.g. /dev/null, /dev/urandom)
    outHostPath = guestPath;
    return true;
}

int VirtualFilesystem::vfsStat(int vmId, const std::string& guestPath, struct stat* outStat) {
    std::string hostPath;
    if (!translatePath(vmId, guestPath, hostPath) || hostPath.empty()) {
        errno = ENOENT;
        return -1;
    }
    return stat(hostPath.c_str(), outStat);
}

int VirtualFilesystem::vfsAccess(int vmId, const std::string& guestPath, int mode) {
    std::string hostPath;
    if (!translatePath(vmId, guestPath, hostPath) || hostPath.empty()) {
        errno = ENOENT;
        return -1;
    }
    return access(hostPath.c_str(), mode);
}

std::string VirtualFilesystem::getVirtualSelinuxContext(int vmId, const std::string& guestPath) {
    std::lock_guard<std::mutex> lock(mutex_);
    auto it = vmConfigs_.find(vmId);
    bool root = (it != vmConfigs_.end()) ? it->second.rootEnabled : false;

    if (guestPath.rfind("/system/xbin/su", 0) == 0) {
        return root ? "u:object_r:su_exec:s0" : "u:object_r:system_file:s0";
    }
    if (guestPath.rfind("/system", 0) == 0) {
        return "u:object_r:system_file:s0";
    }
    if (guestPath.rfind("/data", 0) == 0) {
        return "u:object_r:system_data_file:s0";
    }
    if (guestPath.rfind("/sdcard", 0) == 0 || guestPath.rfind("/storage", 0) == 0) {
        return "u:object_r:storage_file:s0";
    }
    return "u:object_r:rootfs:s0";
}

} // namespace vm
} // namespace untrusted
