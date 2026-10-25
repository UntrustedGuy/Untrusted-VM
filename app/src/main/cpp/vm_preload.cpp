#define _GNU_SOURCE
#include <sys/types.h>
#include <unistd.h>
#include <dlfcn.h>
#include <cerrno>
#include <cstring>
#include <cstdlib>
#include <android/log.h>

#define LOG_TAG "UntrustedVM-Preload"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

#ifndef RTLD_NEXT
#define RTLD_NEXT ((void *) -1l)
#endif

// Function pointer types for real libc implementations
typedef uid_t (*real_getuid_t)();
typedef uid_t (*real_geteuid_t)();
typedef gid_t (*real_getgid_t)();
typedef gid_t (*real_getegid_t)();
typedef int (*real_setuid_t)(uid_t);
typedef int (*real_setgid_t)(gid_t);

static real_getuid_t real_getuid = nullptr;
static real_geteuid_t real_geteuid = nullptr;
static real_getgid_t real_getgid = nullptr;
static real_getegid_t real_getegid = nullptr;
static real_setuid_t real_setuid = nullptr;
static real_setgid_t real_setgid = nullptr;

static void init_real_functions() {
    if (!real_getuid) {
        real_getuid = (real_getuid_t)dlsym(RTLD_NEXT, "getuid");
        real_geteuid = (real_geteuid_t)dlsym(RTLD_NEXT, "geteuid");
        real_getgid = (real_getgid_t)dlsym(RTLD_NEXT, "getgid");
        real_getegid = (real_getegid_t)dlsym(RTLD_NEXT, "getegid");
        real_setuid = (real_setuid_t)dlsym(RTLD_NEXT, "setuid");
        real_setgid = (real_setgid_t)dlsym(RTLD_NEXT, "setgid");
    }
}

// In guest root context, the process sees itself as genuine root (UID 0)
extern "C" uid_t getuid() {
    init_real_functions();
    const char* rootEnv = getenv("UNTRUSTED_VM_ROOT");
    if (rootEnv && strcmp(rootEnv, "1") == 0) {
        return 0; // root
    }
    return real_getuid ? real_getuid() : 10100;
}

extern "C" uid_t geteuid() {
    init_real_functions();
    const char* rootEnv = getenv("UNTRUSTED_VM_ROOT");
    if (rootEnv && strcmp(rootEnv, "1") == 0) {
        return 0; // root
    }
    return real_geteuid ? real_geteuid() : 10100;
}

extern "C" gid_t getgid() {
    init_real_functions();
    const char* rootEnv = getenv("UNTRUSTED_VM_ROOT");
    if (rootEnv && strcmp(rootEnv, "1") == 0) {
        return 0; // root
    }
    return real_getgid ? real_getgid() : 10100;
}

extern "C" gid_t getegid() {
    init_real_functions();
    const char* rootEnv = getenv("UNTRUSTED_VM_ROOT");
    if (rootEnv && strcmp(rootEnv, "1") == 0) {
        return 0; // root
    }
    return real_getegid ? real_getegid() : 10100;
}

extern "C" int setuid(uid_t uid) {
    const char* rootEnv = getenv("UNTRUSTED_VM_ROOT");
    if (rootEnv && strcmp(rootEnv, "1") == 0) {
        // In virtual root mode, allow guest to switch UIDs seamlessly
        return 0;
    }
    init_real_functions();
    return real_setuid ? real_setuid(uid) : 0;
}

extern "C" int setgid(gid_t gid) {
    const char* rootEnv = getenv("UNTRUSTED_VM_ROOT");
    if (rootEnv && strcmp(rootEnv, "1") == 0) {
        return 0;
    }
    init_real_functions();
    return real_setgid ? real_setgid(gid) : 0;
}

extern "C" int seteuid(uid_t euid) {
    return setuid(euid);
}

extern "C" int setegid(gid_t egid) {
    return setgid(egid);
}
