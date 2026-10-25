#pragma once

#include <string>
#include <vector>

namespace untrusted {
namespace vm {

struct ProcessResult {
    int exitCode{0};
    std::string output;
};

class ProcessExecutor {
public:
    static ProcessExecutor& getInstance();

    ProcessResult execute(
        int vmId,
        const std::string& command,
        bool requestRoot,
        const std::string& rootfsPath,
        const std::string& preloadPath
    );

private:
    ProcessExecutor() = default;
};

} // namespace vm
} // namespace untrusted
