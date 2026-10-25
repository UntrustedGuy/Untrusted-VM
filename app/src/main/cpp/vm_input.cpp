#include "vm_input.h"
#include <chrono>

namespace untrusted {
namespace vm {

static uint64_t currentMillis() {
    return std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now().time_since_epoch()
    ).count();
}

InputManager& InputManager::getInstance() {
    static InputManager instance;
    return instance;
}

void InputManager::injectTouch(int vmId, int action, float x, float y, int pointerId) {
    std::lock_guard<std::mutex> lock(inputMutex_);
    TouchEvent ev;
    ev.action = action;
    ev.x = x;
    ev.y = y;
    ev.pointerId = pointerId;
    ev.timestamp = currentMillis();

    auto& q = touchQueues_[vmId];
    if (q.size() > 128) {
        q.pop(); // Drop oldest event if buffer is full
    }
    q.push(ev);
}

void InputManager::injectKey(int vmId, int keyCode, int action) {
    std::lock_guard<std::mutex> lock(inputMutex_);
    KeyEvent ev;
    ev.keyCode = keyCode;
    ev.action = action;
    ev.timestamp = currentMillis();

    auto& q = keyQueues_[vmId];
    if (q.size() > 64) {
        q.pop();
    }
    q.push(ev);
}

bool InputManager::pollTouch(int vmId, TouchEvent& outEvent) {
    std::lock_guard<std::mutex> lock(inputMutex_);
    auto it = touchQueues_.find(vmId);
    if (it != touchQueues_.end() && !it->second.empty()) {
        outEvent = it->second.front();
        it->second.pop();
        return true;
    }
    return false;
}

bool InputManager::pollKey(int vmId, KeyEvent& outEvent) {
    std::lock_guard<std::mutex> lock(inputMutex_);
    auto it = keyQueues_.find(vmId);
    if (it != keyQueues_.end() && !it->second.empty()) {
        outEvent = it->second.front();
        it->second.pop();
        return true;
    }
    return false;
}

} // namespace vm
} // namespace untrusted
