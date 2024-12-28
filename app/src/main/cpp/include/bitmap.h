//
// Created by tainzhi on 12/12/24.
// Email: qfq61@qq.com
//

#ifndef TCAMERA_BITMAP_H
#define TCAMERA_BITMAP_H

#include <jni.h>
#include <cassert>
#include <android/bitmap.h>
#include "util.h"

struct Bitmap {
public:
    Bitmap(JNIEnv *env, jobject bitmap);
    Bitmap(const Bitmap &bitmap) = delete;
    Bitmap(Bitmap &&bitmap) noexcept;
    Bitmap& operator=(const Bitmap &bitmap) = delete;
    ~Bitmap();
    void destroy(JNIEnv *env);
    bool render(u_char * data, int width, int height);
private:
    jobject globalRef;
    AndroidBitmapInfo bitmapInfo;
};

#endif //TCAMERA_BITMAP_H
