//
// Created by tainzhi on 12/12/24.
// Email: qfq61@qq.com
//

#include "bitmap.h"

#define TAG "NativeBitmap"

Bitmap::Bitmap(JNIEnv *env, jobject bitmap): globalRef(nullptr), env(env) {
    globalRef = env->NewGlobalRef(bitmap);
    if (globalRef == nullptr) {
        LOGE("%s, faild to get global ref for bitmap", __FUNCTION__ );
        return;
    }
    if (ANDROID_BITMAP_RESULT_SUCCESS != AndroidBitmap_getInfo(env, bitmap, &bitmapInfo)) {
        LOGE("%s, get bitmap info failed", __FUNCTION__ );
        return;
    }
}

Bitmap::Bitmap(Bitmap &&bitmap) noexcept: globalRef(bitmap.globalRef), bitmapInfo(bitmap.bitmapInfo) {
    bitmap.globalRef = nullptr;
}

Bitmap::~Bitmap() {
    if (globalRef != nullptr) {
        LOGE("%s, bitmap was not destroyed", __FUNCTION__);
    }
}

bool Bitmap::render(std::shared_ptr<cv::Mat> image)  {
    LOGD("%s", __FUNCTION__ );
    void *dstBuf;
    if (AndroidBitmap_lockPixels(env, globalRef, &dstBuf) < 0) {
        LOGE("%s, lock bitmap failed", __FUNCTION__);
        return false;
    }
    for (int y = 0; y < image->rows; ++y) {
        auto line = (u_char *) dstBuf + y * bitmapInfo.stride;
        memcpy(line, image->data + y, image->cols * 4);
    }
    AndroidBitmap_unlockPixels(env, globalRef);
}

void Bitmap::destroy(JNIEnv *env) {
    if (globalRef != nullptr) {
        env->DeleteGlobalRef(globalRef);
        globalRef = nullptr;
    }
}

