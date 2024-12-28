//
// Created by tainzhi on 12/12/24.
// Email: qfq61@qq.com
//

#include "bitmap.h"

#define TAG "NativeBitmap"

Bitmap::Bitmap(JNIEnv *env, jobject bitmap): globalRef(nullptr) {
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

bool Bitmap::render(u_char * data, int width, int height)  {
    LOGD("%s", __FUNCTION__ );
    JNIEnv *env;
    Util::get_env(&env);
    void *dstBuf;
    if (AndroidBitmap_lockPixels(env, globalRef, &dstBuf) < 0) {
        LOGE("%s, lock bitmap failed", __FUNCTION__);
        return false;
    }
    if (bitmapInfo.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("%s, only support RGBA_8888", __FUNCTION__);
        AndroidBitmap_unlockPixels(env, globalRef);
        return false;
    }
    for (int y = 0; y < height; ++y) {
        auto line = (u_char *) dstBuf + y * bitmapInfo.stride;
        memcpy(line, data + y, width * 4);
    }
    AndroidBitmap_unlockPixels(env, globalRef);
}

void Bitmap::destroy(JNIEnv *env) {
    if (globalRef != nullptr) {
        env->DeleteGlobalRef(globalRef);
        globalRef = nullptr;
    }
}

