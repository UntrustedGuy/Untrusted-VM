# Untrusted VM

A high-performance, standalone Android-in-Android virtualization container app with per-instance root support, running on non-rooted Android host devices without requiring Shizuku, root, or PC-based activations.

Inspired by the reverse-engineering analysis of VMOS Pro (`vmospro.apk`).

---

## Architecture Overview

Untrusted VM operates as an unprivileged user-space container (running under stock Android app UID `10xxx`) that isolates and executes guest Android environments directly on the host Linux kernel.

```
+-------------------------------------------------------------------------+
|                              Host Android OS                            |
|                          (Unprivileged UID 10xxx)                       |
|                                                                         |
|  +--------------------+  +----------------------+  +-----------------+  |
|  | Untrusted VM App UI|  |  FloatingVmService   |  |  HalBoxManager  |  |
|  |  (VmSurfaceView)   |  | (Overlays at 60 FPS) |  | (Sensors / GPS) |  |
|  +---------+----------+  +----------+-----------+  +--------+--------+  |
|            |                        |                       |           |
|            +-------------------+----+-----------------------+           |
|                                |                                        |
|                     JNI (libvmcore.so / C++)                            |
|                                |                                        |
|  +--------------------+--------+----------------+--------------------+  |
|  |   Virtual VFS      |  Native Framebuffer     |  Process Executor  |  |
|  | (vm_vfs.cpp)       |  (vm_framebuffer.cpp)   |  (vm_process.cpp)  |  |
|  | - Redirection      |  - Double Buffering     |  - fork() / execve |  |
|  | - Fake SELinux     |  - Direct Bitmap blit   |  - pipe() IPC      |  |
|  +--------------------+-------------------------+--------------------+  |
|                                                                         |
|                 Multi-Process Services (:vms01 - :vms64)                |
+------------------------------------+------------------------------------+
                                     |
                                     v
+-------------------------------------------------------------------------+
|                            Guest VM Instance                            |
|                                                                         |
|  Root Enabled (Virtual UID 0)          Root Disabled (Stock Userland)   |
|  - /system/xbin/su (0755)              - su masked / returns ENOENT     |
|  - LD_PRELOAD=libvm_preload.so         - ro.build.tags=release-keys     |
|  - getuid() -> 0                       - getuid() -> 10100              |
|  - ro.build.tags=test-keys             - Standard Android sandbox       |
+-------------------------------------------------------------------------+
```

---

## Key Features

1. **Standalone Non-Root Operation**
   - Operates completely within stock Android userspace sandboxing.
   - Zero dependency on Shizuku, wireless debugging bridges, or PC ADB daemons.

2. **Per-Instance Virtual Root (`persist.root.enable`)**
   - Independent root toggle for every virtual machine.
   - When enabled, `libvm_preload.so` intercepts `getuid()`, `geteuid()`, `getgid()`, `setuid()`, and `capget()` to simulate true root (`uid=0`) for guest apps.
   - Bundled `/system/xbin/su` binary and `SuPolicyManager` for interactive prompt management (`ALLOW`, `DENY`, `PROMPT`).

3. **High-Performance Display Pipeline (`VmSurfaceView`)**
   - Dedicated 60 FPS render thread backed by `libvmcore.so` native framebuffer manager.
   - Low-latency touch event injection mapping host touch coordinates directly into guest viewport resolution.

4. **Floating Window / Picture-in-Picture Mode (`FloatingVmService`)**
   - Overlays a miniature interactive VM window on top of other Android applications with smooth drag, drop, and resize capabilities.

5. **Hardware Abstraction Layer (`HalBoxManager` & `vm_hal.cpp`)**
   - Sensor bridge: Forwards host accelerometer, gyroscope, and light sensor events.
   - Virtual GPS / NMEA: Simulates `$GPGGA` sentences for location spoofing inside the guest.
   - Power supply sysfs emulation: Generates virtual battery status, temperature, and voltage.

6. **File Transfer Station (`FileTransferStation.kt`)**
   - Shared bridge between host storage and guest rootfs (`/sdcard/VMOSfiletransferstation`).
   - Supports importing external APKs, ROM images, and documents into guest storage, and exporting files back to the host.

---

## Building from Source

### Prerequisites
- JDK 17
- Android SDK (API Level 34)
- Android NDK (r26+ / `26.1.10909125`)
- CMake 3.22.1+

### Build Commands
```bash
# Build the native C++ virtualization libraries (libvmcore.so & libvm_preload.so)
# and assemble the debug APK:
./gradlew assembleDebug

# Run the unit test suite:
./gradlew testDebugUnitTest
```

---

## Automated CI/CD
GitHub Actions automated build pipeline configured at `.github/workflows/build.yml`.
Every push to `main` builds the NDK binaries and generates the debug APK artifact ready for installation.
