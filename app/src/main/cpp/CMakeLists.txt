# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html.
# For more examples on how to use CMake, see https://github.com/android/ndk-samples.

# Sets the minimum CMake version required for this project.
cmake_minimum_required(VERSION 3.22.1)

project("tcamera")

# -Wall 标准警告
# -Werror 将警告视为错误，提高代码质量
# 若是有返回值的function忘记返回值，则报错，停止编译
# 因为我再开发中有很多次忘记写返回值bool，导致莫名地报错，等尝试完各种方法才发现，导致浪费了很多时间
# -Wno-unused-function 禁用该警告
#set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wall -Werror -Wmissing-method-return-type -Wno-unused-function")
set(CMAKE_BUILD_TYPE Debug)
set(EXPORT_COMPILE_COMMANDS ON)

message(DEBUG "CMAKE_SOURCE_DIR: ${CMAKE_SOURCE_DIR}/..")
message(DEBUG "CMAKE_PROJECT_DIR: ${CMAKE_PROJECT_PATH}")
message(DEBUG "ANDROID_ABI: ${ANDROID_ABI}")

# import opencv
set(OpenCV_DIR "${CMAKE_SOURCE_DIR}/libs/opencv_4.8/sdk/native/jni")
find_package(OpenCV REQUIRED)
# not need include_directories
#include_directories(${OpenCV_INCLUDE_DIRS})

# import opencl
set(OpenCL_DIR "${CMAKE_SOURCE_DIR}/libs/opencl")
add_library(OpenCL STATIC IMPORTED)
set_target_properties(OpenCL PROPERTIES
        IMPORTED_LOCATION "${OpenCL_DIR}/libOpenCL.a"
        INTERFACES_INCLUDE_DIRECTORIES "${OpenCL_DIR}/include"
)
# not need include_directories
#include_directories(${OpenCL_DIR}/include)

# 定义源码所在目录
file(GLOB SRC "*.cpp")

include_directories(
        include
        ${OpenCV_INCLUDE_DIRS}
        "${OpenCL_DIR}/src/include"
) # Not needed for CMake >= 2.8.11

add_library( # Specifies the name of the library.
        image-processor
        # Sets the library as a shared library.
        SHARED
        ${SRC}
)
#target_compile_definitions(
#        image-processor
#        PRIVATE
#        NEON_ENABLED
#        ANDROID_ARM_MODE=arm
#        ANDROID_ARM_NEON=true
#)
target_link_libraries(
        image-processor
        ${OpenCV_LIBS}
        android
        jnigraphics # for AndroidBitmap_*
        log
        OpenCL #  add this opencl stub, then can use libOpenCL.so, set by uses-native-library in AndroidManifest.xml
)
