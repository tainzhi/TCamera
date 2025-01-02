//
// Created by tainzhi on 12/12/24.
// Email: qfq61@qq.com
//

#include <opencv2/core/mat.hpp>
#include "bitmap.h"

#define TAG "NativeBitmap"

Bitmap::Bitmap(JNIEnv *env, jobject bitmap) : globalRef(nullptr) {
    globalRef = env->NewGlobalRef(bitmap);
    if (globalRef == nullptr) {
        LOGE("failed to get global ref for bitmap");
        return;
    }
    if (ANDROID_BITMAP_RESULT_SUCCESS != AndroidBitmap_getInfo(env, bitmap, &bitmapInfo)) {
        LOGE("get bitmap info failed");
        return;
    }
}

Bitmap::Bitmap(Bitmap &&bitmap) noexcept: globalRef(bitmap.globalRef), bitmapInfo(bitmap.bitmapInfo) {
    bitmap.globalRef = nullptr;
}

Bitmap::~Bitmap() {
    if (globalRef != nullptr) {
        LOGE("bitmap was not destroyed");
    }
}

bool Bitmap::render(JNIEnv *env, const uint8_t * rgba, int size) {
    LOGD();
    void *dstBuf;
    if (ANDROID_BITMAP_RESULT_SUCCESS !=  AndroidBitmap_lockPixels(env, globalRef, &dstBuf)) {
        LOGE("lock bitmap failed");
        return false;
    }
    if (bitmapInfo.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("only support RGBA_8888");
        AndroidBitmap_unlockPixels(env, globalRef);
        return false;
    }
    assert(bitmapInfo.width * bitmapInfo.height * 4 == size);
    mempcpy((uint8_t *) dstBuf, rgba, size);
    AndroidBitmap_unlockPixels(env, globalRef);
    return true;
}

void Bitmap::destroy(JNIEnv *env) {
    LOGD();
    if (globalRef != nullptr) {
        env->DeleteGlobalRef(globalRef);
        globalRef = nullptr;
    }
}

bool Bitmap::getBitmapData(JNIEnv *env, jobject bitmap, uint8_t **data, int &width, int &height) {
    AndroidBitmapInfo bitmapInfo;
    if (ANDROID_BITMAP_RESULT_SUCCESS != AndroidBitmap_getInfo(env, bitmap, &bitmapInfo)) {
        LOGE("get bitmap info failed");
        return false;
    }
    if (bitmapInfo.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("only support RGBA_8888");
        return false;
    }
    void *buf;
    width = bitmapInfo.width;
    height = bitmapInfo.height;
    *data = (uint8_t *) malloc(width * height * 4);
    if (ANDROID_BITMAP_RESULT_SUCCESS != AndroidBitmap_lockPixels(env, bitmap, &buf)) {
        LOGE("lock bitmap failed!");
        return false;
    }
    memcpy(*data, buf, width * height * 4);
    AndroidBitmap_unlockPixels(env, bitmap);
    return true;
}

