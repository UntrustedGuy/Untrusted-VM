cmake_minimum_required(VERSION 3.22.1)

project("vmcore")

add_library(vmcore SHARED
    vm_core.cpp
    vm_vfs.cpp
    vm_su_daemon.cpp
)

find_library(log-lib log)

target_link_libraries(vmcore
    ${log-lib}
)
