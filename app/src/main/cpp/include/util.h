//
// Created by muqing on 2024/10/10.
// Email: qfq61@qq.com
//

#ifndef TCAMERA_UTIL_H
#define TCAMERA_UTIL_H

#include <cstddef>
#include <string>
#include <fcntl.h>
#include <unistd.h>
#include <android/log.h>
#include <ctime>
#include <jni.h>
#include <vector>

#define LOGV( format, ... )  __android_log_print(ANDROID_LOG_VERBOSE, TAG, "%s: "#format, __FUNCTION__, ##__VA_ARGS__)
#define LOGI( format, ... )  __android_log_print(ANDROID_LOG_INFO, TAG, "%s: "#format, __FUNCTION__, \
##__VA_ARGS__)
#define LOGD( format, ... )  __android_log_print(ANDROID_LOG_DEBUG, TAG, "%s: "#format, __FUNCTION__, ##__VA_ARGS__)
#define LOGE( format, ... )  __android_log_print(ANDROID_LOG_ERROR, TAG, "%s: "#format, __FUNCTION__, ##__VA_ARGS__)

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
    static std::string jstring_to_string(JNIEnv *env, jstring jstr);
    static void jobject_to_stringVector(JNIEnv *env, jobject jList, std::vector<std::string> &result);
    static void jobject_to_intVector(JNIEnv *env, jobject jList, std::vector<int> &result);
    static std::string cachePath;
};


// 必须在java首调用的thread中定义 ImageProcessor 的 global ref, 才能在新的 std::thread 中使用
// 否则直接在 new std::thread 中 attach thread之后是无法 find class的
struct fields_t {
    jclass image_processor;
};

#endif //TCAMERA_UTIL_H