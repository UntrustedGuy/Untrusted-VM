#pragma once

#include <cstdint>
#include <vector>
#include <map>
#include <mutex>
#include <queue>

namespace untrusted {
namespace vm {

struct TouchEvent {
    int action;      // 0: DOWN, 1: UP, 2: MOVE, 3: CANCEL
    float x;
    float y;
    int pointerId;
    uint64_t timestamp;
};

struct KeyEvent {
    int keyCode;
    int action;      // 0: DOWN, 1: UP
    uint64_t timestamp;
};

class InputManager {
public:
    static InputManager& getInstance();

    void injectTouch(int vmId, int action, float x, float y, int pointerId);
    void injectKey(int vmId, int keyCode, int action);

    bool pollTouch(int vmId, TouchEvent& outEvent);
    bool pollKey(int vmId, KeyEvent& outEvent);

private:
    InputManager() = default;

    std::map<int, std::queue<TouchEvent>> touchQueues_;
    std::map<int, std::queue<KeyEvent>> keyQueues_;
    std::mutex inputMutex_;
};

} // namespace vm
} // namespace untrusted
