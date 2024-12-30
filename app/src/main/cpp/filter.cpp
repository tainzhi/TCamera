//
// Created by tainzhi on 12/11/24.
// Email: qfq61@qq.com
//

#include "filter.h"

#define TAG "NativeFilterManager"
#define TEST

FilterManager::~FilterManager() {
    std::unique_lock<std::mutex> lock(mutex);
    while (isRunning()) {
        quitCond.wait(lock);
    }
    LOGD("release FilterManager");
}

bool FilterManager::configureThumbnails(JNIEnv *env, jint thumbnail_width, jint thumbnail_height, jobject filter_names,
                                        jobject filter_tags, jobject filter_thumbnail_bitmaps, jobject lut_bitmaps) {
    LOGD();
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
            LOGE("get thumbnail bitmap[%d] failed", i);
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

bool FilterManager::processThumbnails(YuvBuffer *yuvBuffer, int orientation) {
    LOGD();
    this->orientation = orientation;
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
    LOGD();
    assert(yuvBuffer->width >= thumbnail_width && yuvBuffer->height >= thumbnail_height);
    YuvBuffer centerYuv(thumbnail_width, thumbnail_height);
    yuvBuffer->extractCenter(centerYuv);
    cv::Mat centerMat(thumbnail_height + thumbnail_height / 2, thumbnail_width, CV_8UC1);
    memcpy(centerMat.data, centerYuv.yBuffer, thumbnail_width * thumbnail_height);
    memcpy(centerMat.data + thumbnail_height * thumbnail_width, centerYuv.uvBuffer, thumbnail_width * thumbnail_height / 2);
#ifdef TEST
    // std::string filePath = Util::cachePath + '/' + std::to_string(Util::getCurrentTimestampMs()) + ".420p.yuv";
    // LOGD("thumbnail width:%d, height:%d", thumbnail_width, thumbnail_height);
    // LOGD("save center image to %s", filePath.c_str());
    // Util::dumpBinary(filePath.c_str(), centerMat.data, thumbnail_width * thumbnail_height * 1.5);
    std::string filePath(Util::cachePath + "/center_image_" + std::to_string(Util::getCurrentTimestampMs()) + ".jpg");
    LOGD("save center image to %s", filePath.c_str());
    cv::imwrite(filePath, centerMat, std::vector<int>{cv::IMWRITE_JPEG_QUALITY, 100});
#endif
    cv::Mat rgbMat;
    for (size_t i = 0; i < thumbnailBitmaps.size(); i++) {
        if (filterTags[i] == 0) {
            cv::cvtColor(centerMat, rgbMat, cv::COLOR_YUV420sp2RGBA);
            assert(rgbMat.type() == CV_8UC4);
            LOGD("rgbaMat:width:%d, height:%d, type:%d", rgbMat.cols, rgbMat.rows,
                 rgbMat.type());
#ifdef TEST
            std::string rgbFile(Util::cachePath + "/rgba_" + std::to_string(Util::getCurrentTimestampMs()) + ".png");
            LOGD("save rgba mat to %s", rgbFile.c_str());
            cv::imwrite(rgbFile, rgbMat);
#endif
            thumbnailBitmaps[i].render(rgbMat);
        }
    }
    delete yuvBuffer;
}

bool FilterManager::clearThumbnails(JNIEnv *env) {
    LOGD();
    for (auto &bitmap: thumbnailBitmaps) {
        bitmap.destroy(env);
    }
    for (auto &bitmap: lutBitmaps) {
        bitmap.destroy(env);
    }
    return true;
}