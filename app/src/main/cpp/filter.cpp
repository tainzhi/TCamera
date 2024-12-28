//
// Created by tainzhi on 12/11/24.
// Email: qfq61@qq.com
//

#include "filter.h"

#define TAG "NativeFilterManager"
#define TEST

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
        this->thumbnailBitmaps.emplace_back(env, jthubmnailbitmap);
        // reference [FilterBar.kt] for more information
        // if tag >= 10, the thumbnail needs a lut to generate
        // but tag < 10, the thumbnail not needs lut, so lutBitmaps[i] is nullptr
        jobject jlutbitmap = env->CallObjectMethod(lut_bitmaps, getMethod, i);
        if (jlutbitmap != nullptr) {
            this->lutBitmaps.emplace_back(env, jlutbitmap);
        }
        env->DeleteLocalRef(jthubmnailbitmap);
    }
    env->DeleteLocalRef(thumbnailBitmapsClass);
    return true;
}

bool FilterManager::processThumbnails(YuvBuffer *yuvBuffer) {
    LOGD("%s", __FUNCTION__);
    post(kMessage_ProcessThumbnails, static_cast<void *>(yuvBuffer));
    return true;
}

void FilterManager::handle(int what, void *data) {
    switch (what) {
        case kMessage_ProcessThumbnails: {
            process(reinterpret_cast<YuvBuffer *>(data));
            break;
        }
    }
}

void FilterManager::process(YuvBuffer *yuvBuffer) {
    LOGD("%s", __FUNCTION__);
    auto centerMatPtr = std::make_shared<cv::Mat>(thumbnail_height + thumbnail_height / 2, thumbnail_width, CV_8UC1);
    int centerX = yuvBuffer->width / 2;
    int centerY = yuvBuffer->height / 2;
    for (int y = centerY - thumbnail_height / 2; y < centerY + thumbnail_height / 2; y++) {
        memcpy(centerMatPtr->data + (y - centerY + thumbnail_height / 2) * thumbnail_width,
               yuvBuffer->y + y * yuvBuffer->width + centerX - thumbnail_width / 2, thumbnail_width);
    }
    for (int y = centerY / 2 - thumbnail_height / 4; y < centerY / 2 + thumbnail_height / 4; y++) {
        memcpy(centerMatPtr->data + thumbnail_height * thumbnail_width +
               (y - centerY / 2 + thumbnail_height / 4) * thumbnail_width,
               yuvBuffer->uv + y * yuvBuffer->width + centerX - thumbnail_width / 2, thumbnail_width / 2 * 2);
    }
#ifdef TEST
    std::string filePath = Util::cachePath + '/' + std::to_string(Util::getCurrentTimestampMs()) + ".420p.yuv";
    LOGD("%s, thumbnail width:%d, height:%d", __FUNCTION__, thumbnail_width, thumbnail_height);
    LOGD("%s, save center image to %s", __FUNCTION__, filePath.c_str());
    Util::dumpBinary(filePath.c_str(), centerMatPtr->data, thumbnail_width * thumbnail_height * 1.5);
#endif
    cv::Mat rgbMat;
    for (size_t i = 0; i < thumbnailBitmaps.size(); i++) {
        switch (filterTags[i]) {
            case 0:
                // 虽然camera2采样的是YUV420sp，但是在传入到 JNI 层时，已经转成了 YUV420p
                LOGD("%s, centerMat:width:%d, height:%d, %dx%d", __FUNCTION__, centerMatPtr->cols, centerMatPtr->rows,
                     centerMatPtr->size[0], centerMatPtr->size[1]);
                cv::cvtColor(*centerMatPtr.get(), rgbMat, cv::COLOR_YUV420sp2RGBA);
                thumbnailBitmaps[i].render(centerMatPtr);
                break;
            default:
                break;
        }
    }
    delete yuvBuffer;
}

bool FilterManager::clearThumbnails(JNIEnv *env) {
    LOGD("%s", __FUNCTION__);
    for (auto &bitmap: thumbnailBitmaps) {
        bitmap.destroy(env);
    }
    for (auto &bitmap: lutBitmaps) {
        bitmap.destroy(env);
    }
    return true;
}