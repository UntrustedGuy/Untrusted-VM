#include "vm_framebuffer.h"
#include <android/log.h>
#include <cstring>
#include <chrono>

#define LOG_TAG "UntrustedVM-FB"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace untrusted {
namespace vm {

FrameBufferManager& FrameBufferManager::getInstance() {
    static FrameBufferManager instance;
    return instance;
}

void FrameBufferManager::initBuffer(int vmId, int width, int height) {
    std::lock_guard<std::mutex> lock(mapMutex_);
    FrameBuffer& fb = buffers_[vmId];
    fb.width = width;
    fb.height = height;
    fb.pixels.resize(width * height, 0xFF090C15); // Dark background (ARGB: 0xFF090C15)
    LOGD("Initialized FrameBuffer for VM #%d: %dx%d (%zu pixels)", vmId, width, height, fb.pixels.size());
}

void FrameBufferManager::releaseBuffer(int vmId) {
    std::lock_guard<std::mutex> lock(mapMutex_);
    buffers_.erase(vmId);
    LOGD("Released FrameBuffer for VM #%d", vmId);
}

void FrameBufferManager::updatePixels(int vmId, const uint32_t* srcPixels, int width, int height) {
    std::lock_guard<std::mutex> lock(mapMutex_);
    auto it = buffers_.find(vmId);
    if (it == buffers_.end()) return;

    FrameBuffer& fb = it->second;
    std::lock_guard<std::mutex> bufLock(fb.bufMutex);
    if (fb.width == width && fb.height == height) {
        memcpy(fb.pixels.data(), srcPixels, width * height * sizeof(uint32_t));
    }
}

void FrameBufferManager::renderStatusScreen(int vmId, const std::string& vmName, const std::string& version, bool isRoot) {
    std::lock_guard<std::mutex> lock(mapMutex_);
    auto it = buffers_.find(vmId);
    if (it == buffers_.end()) return;

    FrameBuffer& fb = it->second;
    std::lock_guard<std::mutex> bufLock(fb.bufMutex);

    // Draw stylized dark background with grid
    uint32_t bg = 0xFF0F172A; // Slate dark
    uint32_t gridColor = 0xFF1E293B;
    uint32_t rootColor = isRoot ? 0xFF00E676 : 0xFF64748B; // Neon Green or Muted

    for (int y = 0; y < fb.height; ++y) {
        for (int x = 0; x < fb.width; ++x) {
            uint32_t pixel = bg;
            if (x % 40 == 0 || y % 40 == 0) {
                pixel = gridColor;
            }
            // Top status bar
            if (y < 48) {
                pixel = 0xFF1E1B4B; // Deep Indigo
            }
            // Root indicator line
            if (y >= 48 && y < 52) {
                pixel = rootColor;
            }
            fb.pixels[y * fb.width + x] = pixel;
        }
    }
}

bool FrameBufferManager::copyToBitmap(JNIEnv* env, int vmId, jobject bitmap) {
    if (!bitmap) return false;

    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) {
        LOGE("Failed to get Bitmap info");
        return false;
    }

    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("Bitmap format is not RGBA_8888: %d", info.format);
        return false;
    }

    std::lock_guard<std::mutex> lock(mapMutex_);
    auto it = buffers_.find(vmId);
    if (it == buffers_.end()) return false;

    FrameBuffer& fb = it->second;
    std::lock_guard<std::mutex> bufLock(fb.bufMutex);

    void* dstPixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &dstPixels) < 0) {
        LOGE("Failed to lock Bitmap pixels");
        return false;
    }

    // Direct memcpy row-by-row or block if dimensions match
    int copyWidth = (fb.width < (int)info.width) ? fb.width : (int)info.width;
    int copyHeight = (fb.height < (int)info.height) ? fb.height : (int)info.height;

    uint8_t* dst = (uint8_t*)dstPixels;
    const uint32_t* src = fb.pixels.data();

    for (int y = 0; y < copyHeight; ++y) {
        memcpy(dst + (y * info.stride), src + (y * fb.width), copyWidth * sizeof(uint32_t));
    }

    AndroidBitmap_unlockPixels(env, bitmap);
    return true;
}

int FrameBufferManager::getWidth(int vmId) {
    std::lock_guard<std::mutex> lock(mapMutex_);
    auto it = buffers_.find(vmId);
    return (it != buffers_.end()) ? it->second.width : 720;
}

int FrameBufferManager::getHeight(int vmId) {
    std::lock_guard<std::mutex> lock(mapMutex_);
    auto it = buffers_.find(vmId);
    return (it != buffers_.end()) ? it->second.height : 1280;
}

} // namespace vm
} // namespace untrusted
