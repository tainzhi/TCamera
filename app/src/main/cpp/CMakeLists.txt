# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html.
# For more examples on how to use CMake, see https://github.com/android/ndk-samples.

# Sets the minimum CMake version required for this project.
cmake_minimum_required(VERSION 3.22.1)

project("tcamera")

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wall -DEBUG")
set(CMAKE_BUILD_TYPE Debug)

message(DEBUG "CMAKE_SOURCE_DIR: ${CMAKE_SOURCE_DIR}/..")
message(DEBUG "CMAKE_PROJECT_DIR: ${CMAKE_PROJECT_PATH}")
message(DEBUG "ANDROID_ABI: ${ANDROID_ABI}")

set(OpenCV_DIR "${CMAKE_SOURCE_DIR}/opencv_4.8/sdk/native/jni")
find_package(OpenCV REQUIRED)
include_directories(${OpenCV_INCLUDE_DIRS})

# 定义源码所在目录
file(GLOB SRC "*.cpp")

include_directories(
        include
        #        ${OpenCV_}
) # Not needed for CMake >= 2.8.11

add_library( # Specifies the name of the library.
        image-processor
        # Sets the library as a shared library.
        SHARED
        ${SRC}
)
target_link_libraries(
        image-processor
        ${OpenCV_LIBS}
        android
        log
)
