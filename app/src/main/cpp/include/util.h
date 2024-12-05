//
// Created by muqing on 2024/10/10.
// Email: qfq61@qq.com
//

#ifndef TCAMERA_UTIL_H
#define TCAMERA_UTIL_H

#include <stddef.h>
#include <string>
#include <fcntl.h>
#include <unistd.h>
#include <android/log.h>
#include <time.h>
#include <jni.h>

#define LOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

class Util {
public:
    /**
     * 获取当前时间戳，以毫秒为单位
     */
    static unsigned long long getCurrentTimestampMs();
    static bool dumpBinary( const char * path, void *data, size_t size);
    static JavaVM *gCachedJavaVm __attribute__ ((visibility(("default"))));
    static bool get_env(JNIEnv **);
    static void release_env();
    static void handleEnvException(JNIEnv * env);
};


// 必须在java首调用的thread中定义 ImageProcessor 的 global ref, 才能在新的 std::thread 中使用
// 否则直接在 new std::thread 中 attach thread之后是无法 find class的
struct fields_t {
    jclass image_processor;
    jmethodID post_from_native;
};

#endif //TCAMERA_UTIL_H
