#pragma once

#include <string>
#include <vector>
#include <map>
#include <mutex>
#include <sys/stat.h>

namespace untrusted {
namespace vm {

class VirtualFilesystem {
public:
    static VirtualFilesystem& getInstance();

    void registerVmRootfs(int vmId, const std::string& rootfsPath, bool rootEnabled);
    void updateRootOption(int vmId, bool rootEnabled);
    void unregisterVm(int vmId);

    // Path translation from Guest path to Host storage path
    bool translatePath(int vmId, const std::string& guestPath, std::string& outHostPath);

    // Direct filesystem operations with virtualized semantics
    int vfsStat(int vmId, const std::string& guestPath, struct stat* outStat);
    int vfsAccess(int vmId, const std::string& guestPath, int mode);

    // SELinux context resolution
    std::string getVirtualSelinuxContext(int vmId, const std::string& guestPath);

private:
    VirtualFilesystem() = default;

    struct VmFsConfig {
        std::string rootfsPath;
        bool rootEnabled{false};
    };

    std::map<int, VmFsConfig> vmConfigs_;
    std::mutex mutex_;
};

} // namespace vm
} // namespace untrusted
