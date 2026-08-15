cmake_minimum_required(VERSION 3.22.1)

project(rpcsx-jni LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

if(CMAKE_SYSTEM_PROCESSOR MATCHES "aarch64")
    add_subdirectory(libadrenotools)
else()
    add_library(adrenotools INTERFACE)
endif()

# The JNI GLUE, not the emulator core. Produces librpcsx-jni.so, which
# System.loadLibrary("rpcsx-jni") brings up; it then dlopen()s the core
# (librpcsx-core.so, shipped as a prebuilt in jniLibs) and forwards to its
# _rpcsx_* entry points. The glue is the official RPCSX android UI's
# native-lib.cpp, vendored into this repo.
add_library(rpcsx-jni SHARED
    native-lib.cpp
)

target_link_libraries(rpcsx-jni
    android
    log
    adrenotools
)
