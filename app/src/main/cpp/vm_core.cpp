#include <jni.h>
#include <string>
#include <android/log.h>
#include "vm_vfs.h"
#include "vm_process.h"
#include "vm_framebuffer.h"
#include "vm_input.h"
#include "vm_hal.h"

#define LOG_TAG "UntrustedVM-Core"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

using namespace untrusted::vm;

extern "C" {

JNIEXPORT jint JNICALL
Java_untrusted_vm_core_VmEngine_nativeInitSandbox(
    JNIEnv* env,
    jobject /* this */,
    jint vmId,
    jstring rootfsPath,
    jboolean rootEnabled,
    jint width,
    jint height
) {
    const char* pathStr = env->GetStringUTFChars(rootfsPath, nullptr);
    std::string path(pathStr);
    env->ReleaseStringUTFChars(rootfsPath, pathStr);

    VirtualFilesystem::getInstance().registerVmRootfs(vmId, path, rootEnabled);
    FrameBufferManager::getInstance().initBuffer(vmId, width, height);
    FrameBufferManager::getInstance().renderStatusScreen(vmId, "Untrusted VM", "14.0", rootEnabled);

    LOGI("nativeInitSandbox initialized for VM #%d at %s (root: %d)", vmId, path.c_str(), rootEnabled);
    return 0;
}

JNIEXPORT jboolean JNICALL
Java_untrusted_vm_core_VmEngine_nativeSetRootOption(
    JNIEnv* /* env */,
    jobject /* this */,
    jint vmId,
    jboolean enabled
) {
    VirtualFilesystem::getInstance().updateRootOption(vmId, enabled);
    FrameBufferManager::getInstance().renderStatusScreen(vmId, "Untrusted VM", "14.0", enabled);
    LOGI("nativeSetRootOption updated for VM #%d -> %d", vmId, enabled);
    return JNI_TRUE;
}

JNIEXPORT jobject JNICALL
Java_untrusted_vm_core_VmEngine_nativeExecuteCommand(
    JNIEnv* env,
    jobject /* this */,
    jint vmId,
    jstring command,
    jboolean requestRoot,
    jstring rootfsPath,
    jstring preloadPath
) {
    const char* cmdStr = env->GetStringUTFChars(command, nullptr);
    const char* rfsStr = env->GetStringUTFChars(rootfsPath, nullptr);
    const char* preStr = preloadPath ? env->GetStringUTFChars(preloadPath, nullptr) : nullptr;

    std::string cmd(cmdStr);
    std::string rfs(rfsStr);
    std::string pre = preStr ? std::string(preStr) : "";

    env->ReleaseStringUTFChars(command, cmdStr);
    env->ReleaseStringUTFChars(rootfsPath, rfsStr);
    if (preloadPath && preStr) {
        env->ReleaseStringUTFChars(preloadPath, preStr);
    }

    ProcessResult res = ProcessExecutor::getInstance().execute(vmId, cmd, requestRoot, rfs, pre);

    // Instantiate untrusted.vm.core.VmEngine$ExecutionResult(int exitCode, String output)
    jclass resCls = env->FindClass("untrusted/vm/core/VmEngine$ExecutionResult");
    jmethodID ctor = env->GetMethodID(resCls, "<init>", "(ILjava/lang/String;)V");
    jstring outStr = env->NewStringUTF(res.output.c_str());

    return env->NewObject(resCls, ctor, res.exitCode, outStr);
}

JNIEXPORT jboolean JNICALL
Java_untrusted_vm_core_VmEngine_nativeGetFrameBuffer(
    JNIEnv* env,
    jobject /* this */,
    jint vmId,
    jobject bitmap
) {
    return FrameBufferManager::getInstance().copyToBitmap(env, vmId, bitmap) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_untrusted_vm_core_VmEngine_nativeInjectTouch(
    JNIEnv* /* env */,
    jobject /* this */,
    jint vmId,
    jint action,
    jfloat x,
    jfloat y,
    jint pointerId
) {
    InputManager::getInstance().injectTouch(vmId, action, x, y, pointerId);
}

JNIEXPORT void JNICALL
Java_untrusted_vm_core_VmEngine_nativeInjectKey(
    JNIEnv* /* env */,
    jobject /* this */,
    jint vmId,
    jint keyCode,
    jint action
) {
    InputManager::getInstance().injectKey(vmId, keyCode, action);
}

JNIEXPORT void JNICALL
Java_untrusted_vm_core_VmEngine_nativeSetGpsLocation(
    JNIEnv* /* env */,
    jobject /* this */,
    jint vmId,
    jdouble latitude,
    jdouble longitude,
    jdouble altitude
) {
    HalBox::getInstance().setGpsLocation(vmId, latitude, longitude, altitude);
}

JNIEXPORT void JNICALL
Java_untrusted_vm_core_VmEngine_nativeSetBattery(
    JNIEnv* /* env */,
    jobject /* this */,
    jint vmId,
    jint level,
    jboolean isCharging,
    jint temperature
) {
    HalBox::getInstance().setBattery(vmId, level, isCharging, temperature);
}

JNIEXPORT jstring JNICALL
Java_untrusted_vm_core_VmEngine_nativeTranslatePath(
    JNIEnv* env,
    jobject /* this */,
    jint vmId,
    jstring guestPath
) {
    const char* gpStr = env->GetStringUTFChars(guestPath, nullptr);
    std::string gp(gpStr);
    env->ReleaseStringUTFChars(guestPath, gpStr);

    std::string hp;
    VirtualFilesystem::getInstance().translatePath(vmId, gp, hp);
    return env->NewStringUTF(hp.c_str());
}

} // extern "C"
