cmake_minimum_required(VERSION 3.22.1)

project("kewmobile")

include(FetchContent)
FetchContent_Declare(
        libusb
        GIT_REPOSITORY https://github.com/libusb/libusb.git
        GIT_TAG master
)
FetchContent_MakeAvailable(libusb)

# Execute gen-describe.sh to create version_describe.h
execute_process(
    COMMAND sh ${libusb_SOURCE_DIR}/build-aux/gen-describe.sh ${libusb_SOURCE_DIR} ${libusb_SOURCE_DIR}/libusb/version_describe.h
    WORKING_DIRECTORY ${libusb_SOURCE_DIR}
)

add_library(
        kewmobile
        SHARED
        native-lib.cpp
        ${libusb_SOURCE_DIR}/libusb/core.c
        ${libusb_SOURCE_DIR}/libusb/descriptor.c
        ${libusb_SOURCE_DIR}/libusb/hotplug.c
        ${libusb_SOURCE_DIR}/libusb/io.c
        ${libusb_SOURCE_DIR}/libusb/sync.c
        ${libusb_SOURCE_DIR}/libusb/strerror.c
        ${libusb_SOURCE_DIR}/libusb/os/linux_usbfs.c
        ${libusb_SOURCE_DIR}/libusb/os/events_posix.c
        ${libusb_SOURCE_DIR}/libusb/os/threads_posix.c
        ${libusb_SOURCE_DIR}/libusb/os/linux_netlink.c
)

find_library(log-lib log)

target_include_directories(kewmobile PRIVATE 
    ${libusb_SOURCE_DIR}/android
    ${libusb_SOURCE_DIR}/libusb
    ${libusb_SOURCE_DIR}/libusb/os
)

target_compile_definitions(kewmobile PRIVATE -D_GNU_SOURCE=1)

target_compile_options(kewmobile PRIVATE -fvisibility=hidden -pthread)

target_link_libraries(
        kewmobile
        ${log-lib}
        android)
