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
};

#endif //TCAMERA_UTIL_H
