//
// Created by muqing on 2024/10/10.
// Email: qfq61@qq.com
//

#include "util.h"

#define TAG "NativeUtil"

unsigned long long Util::getCurrentTimestampMs()
{
    struct timeval tv;
    gettimeofday(&tv, nullptr);
    unsigned long long timestampMs;
    timestampMs = ((unsigned long long)tv.tv_sec * 1000) + (tv.tv_usec / 1000000);
    return timestampMs;
}

bool Util::dumpBinary(const char * path, void *data, size_t size)
{
    bool success = false;
    int file_fd = open(path, O_RDWR | O_CREAT, 0777);
    if (file_fd >= 0)
    {
        int32_t written = write(file_fd, data, size);
        if (written < size)
        {
            LOGE("%s Error in write binary to %s", __FUNCTION__, path);
            success = false;
        }
        success = true;
    } else {
        LOGE("%s Error in opening file %s error = %d", __FUNCTION__, path, errno);
        success = false;
    }
    close(file_fd);
    return success;
}

JavaVM * Util::gCachedJavaVm = nullptr;
JNIEnv * Util::gCachedJniEnv = nullptr;