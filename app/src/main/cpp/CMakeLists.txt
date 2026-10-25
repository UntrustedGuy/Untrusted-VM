cmake_minimum_required(VERSION 3.22.1)

project("vmcore")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# 1. Main Virtualization Engine Library (libvmcore.so)
add_library(vmcore SHARED
    vm_core.cpp
    vm_vfs.cpp
    vm_process.cpp
    vm_framebuffer.cpp
    vm_input.cpp
    vm_hal.cpp
)

find_library(log-lib log)
find_library(jnigraphics-lib jnigraphics)

target_link_libraries(vmcore
    ${log-lib}
    ${jnigraphics-lib}
)

# 2. Guest Process Interception Library (libvm_preload.so)
# This library is injected via LD_PRELOAD into guest processes when Root is enabled,
# overriding getuid, geteuid, getgid, setuid, and capability calls to grant UID 0.
add_library(vm_preload SHARED
    vm_preload.cpp
)

target_link_libraries(vm_preload
    ${log-lib}
)
