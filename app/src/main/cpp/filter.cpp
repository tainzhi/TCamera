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

bool FilterManager::processThumbnails(cv::Mat * yuvMat) {
    LOGD("%s", __FUNCTION__);
    post(kMessage_ProcessThumbnails, static_cast<void*>(yuvMat));
    return true;
}

void FilterManager::handle(int what, void *data) {
    switch (what) {
        case kMessage_ProcessThumbnails: {
            process(reinterpret_cast<cv::Mat *>(data));
            break;
        }
    }
}

void FilterManager::process(cv::Mat *mat) {
    LOGD("%s", __FUNCTION__);
    int centerX = mat->cols / 2;
    int centerY = mat->rows / 2;
    cv::Mat yChannel = (*mat)(
            cv::Rect(centerX - thumbnail_width / 2, centerY - thumbnail_height / 2, thumbnail_width, thumbnail_height));
    cv::Mat uChannel = (*(mat + mat->cols * mat->rows))(cv::Rect(centerX / 2 - thumbnail_width / 4,
                                                                centerY / 2 - thumbnail_height / 4, thumbnail_width / 2,
                                                                thumbnail_height / 2));
    cv::Mat vChannel = (*(mat + mat->cols * mat->rows * 5 / 4))(cv::Rect(centerX / 2 - thumbnail_width / 4,
                                                                    centerY / 2 - thumbnail_height / 4, thumbnail_width / 2,
                                                                    thumbnail_height / 2));
    cv::Mat centerMat(thumbnail_height + thumbnail_height / 2, thumbnail_width, CV_8UC1);
    yChannel.copyTo(centerMat(cv::Rect(0, 0, thumbnail_width, thumbnail_height)));
    uChannel.copyTo(centerMat(cv::Rect(0, thumbnail_height, thumbnail_width/2, thumbnail_height/2)));
    vChannel.copyTo(centerMat(cv::Rect(thumbnail_width/2, thumbnail_height, thumbnail_width/2, thumbnail_height/2)));
#ifdef TEST
    std::string filePath = Util::cachePath + '/' +  std::to_string(Util::getCurrentTimestampMs()) + ".jpg";
    LOGD("%s, save center image to %s", __FUNCTION__, filePath.c_str());
    // 把生成的写到jpeg图片写到 filePath， quality 为 100
    cv::imwrite(filePath, centerMat, std::vector<int>{cv::IMWRITE_JPEG_QUALITY, 100});
#endif
    
    cv::Mat rgbMat;
    for (size_t i = 0; i < thumbnailBitmaps.size(); i++) {
        switch(filterTags[i]) {
            case 0:
                // 虽然camera2采样的是YUV420sp，但是在传入到 JNI 层时，已经转成了 YUV420p
                LOGD("%s, centerMat:width:%d, height:%d, %dx%d", __FUNCTION__, centerMat.cols, centerMat.rows, centerMat
                .size[0], centerMat.size[1]);
                cv::cvtColor(centerMat, rgbMat, cv::COLOR_YUV420p2RGBA);
                thumbnailBitmaps[i].render(rgbMat.data, thumbnail_width, thumbnail_height);
                break;
            default:
                break;
        }
    }
    delete mat;
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