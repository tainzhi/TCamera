//
// Created by tainzhi on 2024/11/20.
// Email: qfq61@qq.com
//

#include "listener.h"


#define TAG "NativeListener"

extern fields_t fields;

void Listener::onCaptured(int jobId, std::string cacheImagePath) {
    JNIEnv *env = nullptr;
    Util::get_env(&env);
    if (env == nullptr) {
        LOGE(" failed to get JNIEnv");
        return;
    }
    jmethodID postFromNativeMethod = env->GetStaticMethodID(fields.image_processor, "postFromNative", "(ILjava/lang/String;)V");
    if (postFromNativeMethod == nullptr) {
        LOGE(" failed to get ImageProcessor.postFromNative");
        return;
    }
    jstring imagePath = env->NewStringUTF(cacheImagePath.c_str());
    if (imagePath == nullptr) {
        LOGE("failed to create jstring for %s", cacheImagePath.c_str());
        return;
    }
    env->CallStaticVoidMethod(fields.image_processor, postFromNativeMethod, jobId, imagePath);
    if (env->ExceptionCheck()) {
        Util::handleEnvException(env);
        LOGE("exception occurred while calling postFromNative");
    }
    env->DeleteLocalRef(imagePath);
}
