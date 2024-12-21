//
// Created by tainzhi on 12/11/24.
// Email: qfq61@qq.com
//

#include "filter.h"

#define TAG "NativeFilterManager"

bool FilterManager::configureThumbnails(JNIEnv *env, jint thumbnail_width, jint thumbnail_height, jobject filter_names,
                                        jobject filter_tags, jobject filter_thumbnail_bitmaps, jobject lut_bitmaps) {
    LOGD("%s", __FUNCTION__);
    this->thumbnail_width = thumbnail_width;
    this->thumbnail_height = thumbnail_height;
    Util::jobject_to_stringVector(env, filter_names, this->filterNames);
    Util::jobject_to_intVector(env, filter_tags, this->filterTags);
    jclass thumbnailBitmapsClass = env->GetObjectClass(filter_thumbnail_bitmaps);
    jmethodID sizeMethod = env->GetMethodID(thumbnailBitmapsClass, "size", "()I");
    jmethodID getMethod = env->GetMethodID(thumbnailBitmapsClass, "get", "(I)Ljava/lang/Object;");
    jint size = env->CallIntMethod(filter_thumbnail_bitmaps, sizeMethod);
    for (jint i = 0; i < size; ++i) {
        jobject jthubmnailbitmap = env->CallObjectMethod(filter_thumbnail_bitmaps, getMethod, i);
        if (jthubmnailbitmap == nullptr) {
            LOGE("%s, get thumbnail bitmap[%d] failed", __FUNCTION__, i);
            return false;
        }
        this->thumbnailBitmaps.emplace_back(Bitmap(env, jthubmnailbitmap));
        // reference [FilterBar.kt] for more information
        // if tag >= 10, the thumbnail needs a lut to generate
        // but tag < 10, the thumbnail not needs lut, so lutBitmaps[i] is nullptr
        jobject jlutbitmap = env->CallObjectMethod(lut_bitmaps, getMethod, i);
        if (jlutbitmap != nullptr) {
            this->lutBitmaps.emplace_back(Bitmap(env, jlutbitmap));
        }
        env->DeleteLocalRef(jthubmnailbitmap);
    }
    env->DeleteLocalRef(thumbnailBitmapsClass);
    return true;
}

bool FilterManager::processThumbnails(cv::Mat yuvMat) {
    LOGD("%s", __FUNCTION__);
    post(kMessage_ProcessThumbnails, &yuvMat);
    return true;
}

void FilterManager::handle(int what, void *data) {
    switch (what) {
        case kMessage_ProcessThumbnails: {
            cv::Mat image = *reinterpret_cast<cv::Mat *>(data);
            process(image);
            break;
        }
    }
}

void FilterManager::process(cv::Mat mat) {
    LOGD("%s", __FUNCTION__);
}

bool FilterManager::clearThumbnails(JNIEnv *env) {
    LOGD("%s", __FUNCTION__);
    for(auto &bitmap: thumbnailBitmaps) {
        bitmap.destroy(env);
    }
    for (auto &bitmap: lutBitmaps) {
        bitmap.destroy(env);
    }
    return true;
}