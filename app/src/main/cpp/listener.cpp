//
// Created by tainzhi on 2024/11/20.
// Email: qfq61@qq.com
//

#include "listener.h"


#define TAG "NativeListener"
void Listener::onCaptured(int jobId, std::string cacheImagePath) {
    JNIEnv *env = nullptr;
    jint result = Util::gCachedJavaVm->GetEnv((void **) &env, JNI_VERSION_1_6);
    if (result == JNI_EDETACHED) {
        result = Util::gCachedJavaVm->AttachCurrentThread(&env, nullptr);
        if (result != JNI_OK) {
            LOGE("%s, failed to attache current thread to JVM", __FUNCTION__);
            return ;
        }
    } else if (result == JNI_EVERSION) {
        LOGE("%s, JNI interface version is not supported", __FUNCTION__ );
        return;
    }
    if (env == nullptr) {
        LOGE("%s, failed to get JNIEnv", __FUNCTION__ );
        return;
    }
    jclass jc = env->FindClass("com/tainzhi/android/tcamera/ImageProcessor");
    if (jc == nullptr) {
        LOGE("%s, failed to find ImageProcessor class", __FUNCTION__ );
        return;
    }
    jmethodID jmethodId = env->GetStaticMethodID(jc, "postFromNative", "(ILjava/lang/String:)V");
    if (jmethodId == nullptr) {
        LOGE("%s, failed to get ImageProcessor.postFromNative");
        return;
    }
    env->CallStaticVoidMethod(jc, jmethodId, jobId, env->NewStringUTF(cacheImagePath.c_str()));
    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
        LOGE("%s, exception occurred while calling postFromNative", __FUNCTION__ );
    }
    
}
