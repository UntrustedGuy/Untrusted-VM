#include <jni.h>
#include <string>
#include <android/log.h>
#include <unistd.h>
#include <sys/types.h>

#define LOG_TAG "UntrustedVM-Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jint JNICALL
Java_untrusted_vm_core_VmEngine_nativeInitSandbox(
    JNIEnv* env,
    jobject /* this */,
    jint vmId,
    jstring rootfsPath,
    jboolean rootEnabled
) {
    const char* path = env->GetStringUTFChars(rootfsPath, nullptr);
    LOGI("Initializing native sandbox container for VM #%d at %s (Root: %s)",
         vmId, path, rootEnabled ? "ENABLED" : "DISABLED");

    uid_t hostUid = getuid();
    LOGI("Host process UID: %d (Stock non-root Android user)", hostUid);

    env->ReleaseStringUTFChars(rootfsPath, path);
    return 0; // SUCCESS
}

extern "C" JNIEXPORT jboolean JNICALL
Java_untrusted_vm_core_VmEngine_nativeSetRootOption(
    JNIEnv* env,
    jobject /* this */,
    jint vmId,
    jboolean enabled
) {
    LOGI("Native root option toggled for VM #%d -> %s", vmId, enabled ? "TRUE" : "FALSE");
    return JNI_TRUE;
}
