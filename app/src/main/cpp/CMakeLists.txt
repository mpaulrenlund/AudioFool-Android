cmake_minimum_required(VERSION 3.22)
project(audiofooltags CXX)

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
