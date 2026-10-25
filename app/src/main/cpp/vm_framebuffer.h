#pragma once

#include <cstdint>
#include <string>
#include <vector>
#include <map>
#include <mutex>
#include <jni.h>
#include <android/bitmap.h>

namespace untrusted {
namespace vm {

class FrameBufferManager {
public:
    static FrameBufferManager& getInstance();

    void initBuffer(int vmId, int width, int height);
    void releaseBuffer(int vmId);

    // Updates a region or full screen with guest pixel data
    void updatePixels(int vmId, const uint32_t* srcPixels, int width, int height);

    // Renders test pattern or status display if guest surfaceflinger has not pushed frames
    void renderStatusScreen(int vmId, const std::string& vmName, const std::string& version, bool isRoot);

    // Copies current virtual framebuffer directly to an Android Bitmap (hardware blit)
    bool copyToBitmap(JNIEnv* env, int vmId, jobject bitmap);

    int getWidth(int vmId);
    int getHeight(int vmId);

private:
    FrameBufferManager() = default;

    struct FrameBuffer {
        int width{720};
        int height{1280};
        std::vector<uint32_t> pixels;
        uint64_t lastFrameTime{0};
        std::mutex bufMutex;
    };

    std::map<int, FrameBuffer> buffers_;
    std::mutex mapMutex_;
};

} // namespace vm
} // namespace untrusted
