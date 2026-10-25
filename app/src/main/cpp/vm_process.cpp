#include "vm_process.h"
#include <unistd.h>
#include <sys/wait.h>
#include <sys/types.h>
#include <fcntl.h>
#include <android/log.h>
#include <vector>
#include <sstream>

#define LOG_TAG "UntrustedVM-Proc"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace untrusted {
namespace vm {

ProcessExecutor& ProcessExecutor::getInstance() {
    static ProcessExecutor instance;
    return instance;
}

ProcessResult ProcessExecutor::execute(
    int vmId,
    const std::string& command,
    bool requestRoot,
    const std::string& rootfsPath,
    const std::string& preloadPath
) {
    ProcessResult result;
    int pipefd[2];
    if (pipe(pipefd) == -1) {
        result.exitCode = -1;
        result.output = "Failed to create IPC pipe for command execution";
        return result;
    }

    pid_t pid = fork();
    if (pid == -1) {
        close(pipefd[0]);
        close(pipefd[1]);
        result.exitCode = -1;
        result.output = "Failed to fork guest process";
        return result;
    }

    if (pid == 0) {
        // Child Process
        close(pipefd[0]); // Close read end
        dup2(pipefd[1], STDOUT_FILENO);
        dup2(pipefd[1], STDERR_FILENO);
        close(pipefd[1]);

        // Construct sandboxed environment
        std::string pathEnv = "PATH=" + rootfsPath + "/system/xbin:" +
                              rootfsPath + "/system/bin:/system/bin:/system/xbin:/sbin";
        std::string rootfsEnv = "VIRTUAL_ROOTFS=" + rootfsPath;
        std::string vmIdEnv = "VM_ID=" + std::to_string(vmId);

        std::vector<std::string> envStrings = {
            pathEnv,
            rootfsEnv,
            vmIdEnv,
            "ANDROID_DATA=" + rootfsPath + "/data",
            "ANDROID_ROOT=" + rootfsPath + "/system",
            "ANDROID_STORAGE=" + rootfsPath + "/storage",
            "HOME=" + rootfsPath + "/data",
            "TMPDIR=" + rootfsPath + "/data/local/tmp"
        };

        if (requestRoot) {
            envStrings.push_back("UNTRUSTED_VM_ROOT=1");
            envStrings.push_back("USER=root");
            if (!preloadPath.empty()) {
                envStrings.push_back("LD_PRELOAD=" + preloadPath);
            }
        } else {
            envStrings.push_back("UNTRUSTED_VM_ROOT=0");
            envStrings.push_back("USER=u0_a100");
        }

        std::vector<char*> envp;
        for (auto& s : envStrings) {
            envp.push_back(const_cast<char*>(s.c_str()));
        }
        envp.push_back(nullptr);

        // Execute via host /system/bin/sh with sandboxed environment
        char* const argv[] = {
            const_cast<char*>("/system/bin/sh"),
            const_cast<char*>("-c"),
            const_cast<char*>(command.c_str()),
            nullptr
        };

        execve("/system/bin/sh", argv, envp.data());

        // If execve fails
        const char errMsg[] = "execve failed\n";
        write(STDOUT_FILENO, errMsg, sizeof(errMsg) - 1);
        _exit(127);
    }

    // Parent Process
    close(pipefd[1]); // Close write end

    char buffer[1024];
    std::string output;
    ssize_t bytesRead;
    while ((bytesRead = read(pipefd[0], buffer, sizeof(buffer) - 1)) > 0) {
        buffer[bytesRead] = '\0';
        output.append(buffer, bytesRead);
    }
    close(pipefd[0]);

    int status = 0;
    waitpid(pid, &status, 0);

    if (WIFEXITED(status)) {
        result.exitCode = WEXITSTATUS(status);
    } else if (WIFSIGNALED(status)) {
        result.exitCode = 128 + WTERMSIG(status);
    }

    result.output = output;
    return result;
}

} // namespace vm
} // namespace untrusted
