#include <string>
#include <vector>
#include <thread>
#include <atomic>
#include <mutex>
#include <map>
#include <cstring>
#include <unistd.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <android/log.h>

#define LOG_TAG "UntrustedVM-SuDaemon"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace untrusted {
namespace vm {

class SuDaemonServer {
public:
    static SuDaemonServer& getInstance() {
        static SuDaemonServer instance;
        return instance;
    }

    bool start(int vmId, const std::string& socketPath) {
        std::lock_guard<std::mutex> lock(serverMutex_);
        if (servers_.find(vmId) != servers_.end()) {
            return true; // Already running
        }

        auto state = std::make_shared<ServerState>();
        state->vmId = vmId;
        state->socketPath = socketPath;
        state->isRunning.store(true);

        // Ensure parent directory exists
        size_t lastSlash = socketPath.find_last_of('/');
        if (lastSlash != std::string::npos) {
            std::string parentDir = socketPath.substr(0, lastSlash);
            mkdir(parentDir.c_str(), 0777);
        }

        // Unlink old socket
        unlink(socketPath.c_str());

        int serverFd = socket(AF_UNIX, SOCK_STREAM, 0);
        if (serverFd < 0) {
            LOGE("Failed to create UNIX domain socket for VM #%d: %s", vmId, strerror(errno));
            return false;
        }

        struct sockaddr_un addr;
        memset(&addr, 0, sizeof(addr));
        addr.sun_family = AF_UNIX;
        strncpy(addr.sun_path, socketPath.c_str(), sizeof(addr.sun_path) - 1);

        if (bind(serverFd, (struct sockaddr*)&addr, sizeof(addr)) < 0) {
            LOGE("Failed to bind socket at %s: %s", socketPath.c_str(), strerror(errno));
            close(serverFd);
            return false;
        }

        chmod(socketPath.c_str(), 0777); // Accessible to all guest apps

        if (listen(serverFd, 10) < 0) {
            LOGE("Failed to listen on socket %s: %s", socketPath.c_str(), strerror(errno));
            close(serverFd);
            return false;
        }

        state->serverFd = serverFd;
        state->workerThread = std::thread(&SuDaemonServer::runLoop, this, state);
        servers_[vmId] = state;

        LOGI("SuDaemonServer started successfully for VM #%d on %s", vmId, socketPath.c_str());
        return true;
    }

    void stop(int vmId) {
        std::lock_guard<std::mutex> lock(serverMutex_);
        auto it = servers_.find(vmId);
        if (it != servers_.end()) {
            auto state = it->second;
            state->isRunning.store(false);
            if (state->serverFd >= 0) {
                close(state->serverFd);
                state->serverFd = -1;
            }
            unlink(state->socketPath.c_str());
            if (state->workerThread.joinable()) {
                state->workerThread.join();
            }
            servers_.erase(it);
            LOGI("SuDaemonServer stopped for VM #%d", vmId);
        }
    }

private:
    struct ServerState {
        int vmId = 0;
        std::string socketPath;
        int serverFd = -1;
        std::atomic<bool> isRunning{false};
        std::thread workerThread;
    };

    std::mutex serverMutex_;
    std::map<int, std::shared_ptr<ServerState>> servers_;

    void runLoop(std::shared_ptr<ServerState> state) {
        while (state->isRunning.load()) {
            struct sockaddr_un clientAddr;
            socklen_t clientLen = sizeof(clientAddr);
            int clientFd = accept(state->serverFd, (struct sockaddr*)&clientAddr, &clientLen);
            if (clientFd < 0) {
                if (!state->isRunning.load()) break;
                continue;
            }

            // Handle connection in a dedicated thread
            std::thread([this, state, clientFd]() {
                handleClient(state, clientFd);
            }).detach();
        }
    }

    void handleClient(std::shared_ptr<ServerState> state, int clientFd) {
        // Read peer credentials (PID, UID, GID)
        struct ucred cred;
        socklen_t len = sizeof(cred);
        if (getsockopt(clientFd, SOL_SOCKET, SO_PEERCRED, &cred, &len) == 0) {
            LOGI("Su request from VM #%d: caller PID=%d, UID=%d, GID=%d",
                 state->vmId, cred.pid, cred.uid, cred.gid);
        }

        // Read command request buffer from client
        char buffer[1024];
        memset(buffer, 0, sizeof(buffer));
        ssize_t bytesRead = read(clientFd, buffer, sizeof(buffer) - 1);

        std::string requestedCmd = "/system/bin/sh";
        if (bytesRead > 0) {
            requestedCmd = std::string(buffer);
        }

        // Send grant acknowledgment ("GRANT:0\n")
        const char* grantMsg = "GRANT:0\n";
        write(clientFd, grantMsg, strlen(grantMsg));

        close(clientFd);
    }
};

} // namespace vm
} // namespace untrusted
