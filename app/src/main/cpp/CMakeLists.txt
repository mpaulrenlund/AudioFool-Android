cmake_minimum_required(VERSION 3.22)
# C as well as C++: libusb is written in C (0.4.0).
project(audiofooltags C CXX)

# TagLib 2.x, vendored unchanged in third_party/taglib (MPL 1.1 / LGPL 2.1),
# built static and linked into one small JNI library.
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(BUILD_BINDINGS OFF CACHE BOOL "" FORCE)
set(BUILD_TESTING OFF CACHE BOOL "" FORCE)
set(BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(VISIBILITY_HIDDEN ON CACHE BOOL "" FORCE)
add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/taglib taglib)

add_library(audiofooltags SHARED tagreader_jni.cpp)
target_link_libraries(audiofooltags PRIVATE tag log)

# TagLib's headers include one another by bare name, so every folder that holds
# them is on the include path (as an installed TagLib would have them in one).
set(TAGLIB_SRC ${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/taglib/taglib)
file(GLOB_RECURSE TAGLIB_HEADERS ${TAGLIB_SRC}/*.h)
set(TAGLIB_INCLUDES "")
foreach(header ${TAGLIB_HEADERS})
    get_filename_component(dir ${header} DIRECTORY)
    list(APPEND TAGLIB_INCLUDES ${dir})
endforeach()
list(REMOVE_DUPLICATES TAGLIB_INCLUDES)
target_include_directories(audiofooltags PRIVATE ${TAGLIB_INCLUDES} ${CMAKE_CURRENT_BINARY_DIR}/taglib)
target_link_options(audiofooltags PRIVATE "-Wl,-z,max-page-size=16384")

# libusb 1.0.30, vendored unchanged in third_party/libusb (LGPL 2.1): its own shared
# library, linked dynamically. The sources and config.h are those of its own Android
# build (android/jni/libusb.mk).
set(LIBUSB_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/libusb)
add_library(usb-1.0 SHARED
    ${LIBUSB_ROOT}/libusb/core.c
    ${LIBUSB_ROOT}/libusb/descriptor.c
    ${LIBUSB_ROOT}/libusb/hotplug.c
    ${LIBUSB_ROOT}/libusb/io.c
    ${LIBUSB_ROOT}/libusb/sync.c
    ${LIBUSB_ROOT}/libusb/strerror.c
    ${LIBUSB_ROOT}/libusb/os/linux_usbfs.c
    ${LIBUSB_ROOT}/libusb/os/events_posix.c
    ${LIBUSB_ROOT}/libusb/os/threads_posix.c
    ${LIBUSB_ROOT}/libusb/os/linux_netlink.c)
target_include_directories(usb-1.0 PRIVATE ${LIBUSB_ROOT}/android ${LIBUSB_ROOT}/libusb ${LIBUSB_ROOT}/libusb/os)
target_include_directories(usb-1.0 INTERFACE ${LIBUSB_ROOT}/libusb)
target_compile_options(usb-1.0 PRIVATE -fvisibility=hidden -pthread)
target_link_libraries(usb-1.0 PRIVATE log)
target_link_options(usb-1.0 PRIVATE "-Wl,-z,max-page-size=16384")

# The USB DAC's native side: giving the DAC back to Android's driver (0.3.0), and the
# driver that plays through it (0.4.0).
add_library(audiofoolusb SHARED usbdac_jni.cpp usbaudio_driver.cpp)
target_link_libraries(audiofoolusb PRIVATE usb-1.0 log)
target_link_options(audiofoolusb PRIVATE "-Wl,-z,max-page-size=16384")
