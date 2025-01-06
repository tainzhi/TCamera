//
// Created by tainzhi on 12/11/24.
// Email: qfq61@qq.com
//

#include "filter.h"

#define TAG "NativeFilterManager"
// #define TEST

FilterManager::~FilterManager() {
    std::unique_lock<std::mutex> lock(mutex);
    while (isRunning()) {
        quitCond.wait(lock);
    }
    LOGD("release FilterManager");
}

bool FilterManager::configureThumbnails(JNIEnv *env, jint thumbnail_width, jint thumbnail_height, jobject filter_names,
                                        jobject filter_tags, jobject filter_thumbnail_bitmaps, jobject lut_bitmaps) {
    LOGD("begin");
    this->thumbnailWidth = thumbnail_width;
    this->thumbnailHeight = thumbnail_height;
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
            uint8_t *lutData;
            Bitmap::getBitmapData(env, jlutbitmap, &lutData, lutWidth, lutHeight);
            this->lutTables[filterTags[i]] = lutData;
        }
        env->DeleteLocalRef(jthubmnailbitmap);
        env->DeleteLocalRef(jlutbitmap);
    }
    env->DeleteLocalRef(thumbnailBitmapsClass);
    LOGD("end");
    return true;
}

/*
 * // image orientation, 0, 90, 180, 270, 也就是thumbnail需要旋转的角度
 */
void FilterManager::sendProcessThumbnails(std::shared_ptr<Color::YuvBuffer> yuvBuffer, int orientation, int
updateRangeStart, int
updateRangeEnd) {
    LOGD();
    auto msg = new ThumbnailMsg{yuvBuffer, orientation, updateRangeStart, updateRangeEnd};
    post(kMessage_ProcessThumbnails, msg);
}

void FilterManager::sendApplyFilterEffectToJpeg(int jobId, uint8_t *jpegBytes, int jpegByteSize, int filterTag) {
    LOGD();
    auto msg = new ApplyFilterEffectMsg(jobId, jpegBytes, jpegByteSize, filterTag);
    post(kMessage_ApplyFilterEffectToJpeg, msg);
    
}
void FilterManager::sendClearThumbnails() {
    LOGD();
    post(kMessage_ClearThumbnails, nullptr);
}

void FilterManager::handle(int what, void *data) {
    switch (what) {
        case kMessage_ProcessThumbnails: {
            recvProcessThumbnails(static_cast<ThumbnailMsg *>(data));
            break;
        }
        case kMessage_ApplyFilterEffectToJpeg: {
            recvApplyFilterEffectToJpeg(static_cast<ApplyFilterEffectMsg *>(data));
            break;
        }
        case kMessage_ClearThumbnails: {
            recvClearThumbnails();
            break;
        }
    }
}

bool FilterManager::recvProcessThumbnails(FilterManager::ThumbnailMsg *thumbnailMsg) {
    auto yuvBuffer = thumbnailMsg->yuvBuffer;
    LOGD();
    assert(yuvBuffer->width >= thumbnailWidth && yuvBuffer->height >= thumbnailHeight);
#ifdef TEST
    std::string yuvFilePath = std::format("{}/yuv_{}x{}_{}.420sp.yuv", Util::cachePath, yuvBuffer->width,
                                                yuvBuffer->height, Util::getCurrentTimestampMs());
    LOGD("save rotate yuv to %s", yuvFilePath.c_str());
    Util::dumpBinary(yuvFilePath.c_str(), yuvBuffer->data, yuvBuffer->width * yuvBuffer->height * 3 / 2);
#endif
    // 从yuvBuffer中截取中心区域的yuv数据
    Color::YuvBuffer centerYuv(thumbnailWidth, thumbnailHeight);
    yuvBuffer->extractCenter(centerYuv);
#ifdef TEST
    std::string centerYuvFilePath = std::format("{}/center_{}x{}_{}.420sp.yuv", Util::cachePath, thumbnailWidth,
                                                thumbnailHeight, Util::getCurrentTimestampMs());
    LOGD("save center yuv to %s", centerYuvFilePath.c_str());
    Util::dumpBinary(centerYuvFilePath.c_str(), centerYuv.data, thumbnailWidth * thumbnailHeight * 3 / 2);
#endif
    // 旋转
    Color::YuvBuffer rotateYuv;
    centerYuv.rotate(rotateYuv, thumbnailMsg->orientation);
#ifdef TEST
    std::string rotateYuvFilePath = std::format("{}/rotate_{}x{}_{}.420sp.yuv", Util::cachePath, thumbnailWidth,
                                                thumbnailHeight, Util::getCurrentTimestampMs());
    LOGD("save rotate yuv to %s", rotateYuvFilePath.c_str());
    Util::dumpBinary(rotateYuvFilePath.c_str(), rotateYuv.data, thumbnailWidth * thumbnailHeight * 3 / 2);
#endif
    
    // 生成rgba数据
    uint8_t *rgba = new uint8_t[thumbnailWidth * thumbnailHeight * 4];
    rotateYuv.convertToRGBA8888(rgba);
    uint8_t *renderedRgba = new uint8_t[thumbnailWidth * thumbnailHeight * 4];

    // 新的thread，必须要要获取env
    JNIEnv *env;
    Util::get_env(&env);
    
    LOGD("update filter thumbnail bitmaps in [%d, %d]", thumbnailMsg->updateRangeStart, thumbnailMsg->updateRangeEnd);
    for (size_t i = thumbnailMsg->updateRangeStart; i <= thumbnailMsg->updateRangeEnd; i++) {
        if (filterTags[i] == 0) {
            thumbnailBitmaps[i].render(env, rgba, thumbnailWidth * thumbnailHeight * 4);
        } else {
            renderFilterEffect(filterTags[i], rgba, thumbnailWidth, thumbnailHeight, renderedRgba);
            thumbnailBitmaps[i].render(env, renderedRgba, thumbnailWidth * thumbnailHeight * 4);
        }
    }
    delete[] rgba;
    delete[] renderedRgba;
    delete thumbnailMsg;
    return true;
}

void FilterManager::renderFilterEffect(int filterTag, uint8_t * rgba, int width, int height, uint8_t *renderedRgba) {
    if (filterTag == 0) {
        LOGE("not need to apply filter effect with tag=%d", filterTag);
    } else if (filterTag == 1) {
        // grey
        for (size_t j = 0; j < width * height * 4; j += 4) {
            uint8_t wm = rgba[j] * 0.3 + rgba[j + 1] * 0.59 + rgba[j + 2] * 0.11;
            renderedRgba[j] = wm;
            renderedRgba[j + 1] = wm;
            renderedRgba[j + 2] = wm;
            renderedRgba[j + 3] = rgba[j + 3];
        }
    } else if (filterTag == 2) {
        // black and white
        uint8_t threshold = 0.5 * 255;
        uint8_t mean;
        for (size_t j = 0; j < width * height * 4; j += 4) {
            mean = (rgba[j] + rgba[j + 1] + rgba[j + 2]) / 3.0;
            if (mean > threshold) {
                renderedRgba[j] = 255;
                renderedRgba[j + 1] = 255;
                renderedRgba[j + 2] = 255;
                renderedRgba[j + 3] = rgba[j + 3];
            } else {
                renderedRgba[j] = 0;
                renderedRgba[j + 1] = 0;
                renderedRgba[j + 2] = 0;
                renderedRgba[j + 3] = rgba[j + 3];
            }
        }
    } else if (filterTag == 3) {
        // reverse
        for (size_t j = 0; j < width * height * 4; j += 4) {
            renderedRgba[j] = 255 - rgba[j];
            renderedRgba[j + 1] = 255 - rgba[j + 1];
            renderedRgba[j + 2] = 255 - rgba[j + 2];
            renderedRgba[j + 3] = rgba[j + 3];
        }
    } else if (filterTag == 4) {
        // light
        float *hsl = new float[width * height * 4];
        for (size_t j = 0; j < width * height * 4; j += 4) {
            Color::rgba2hsl(rgba + j, hsl + j);
            hsl[j + 2] += 0.15;
            Color::hsl2rgba(hsl + j, renderedRgba + j);
        }
        delete[] hsl;
    } else if (filterTag == 5) {
        // posterization
        float *hsl = new float[width * height * 4];
        for (size_t j = 0; j < width * height * 4; j+=4 ) {
            float grey = rgba[j] * 0.3 + rgba[j + 1] * 0.59 + rgba[j + 2] * 0.11;
            Color::rgba2hsl(rgba + j, hsl + j);
            if (grey < 0.3 * 255) {
                if (hsl[j] < 0.68 || hsl[j] > 0.66) {
                    hsl[j] = 0.67;
                }
                hsl[j + 1] += 0.3;
            } else if (grey > 0.7 * 255) {
                if (hsl[j] < 0.18 || hsl[j] > 0.16) {
                    hsl[j] = 0.17;
                }
                hsl[j + 1] -= 0.3;
            }
            Color::hsl2rgba(hsl + j, reinterpret_cast<uint8_t *>(renderedRgba + j));
        }
        delete[] hsl;
    } else if (filterTag >=10) {
#ifdef TEST
        uint8_t *yuvlut = new uint8_t[lutWidth * lutHeight * 3 / 2];
            // lut png to bitmap argb888 后，存储为 r，g，b，a分别8bit依次存储
            Color::rgba2yuv(this->lutTables[i], lutWidth, lutHeight , yuvlut);
            std::string lutFilePath = std::format("{}/lut_{}_{}x{}.420sp.yuv", Util::cachePath, filterTags[i],
                                                  lutWidth,
                                                  lutHeight);
            Util::dumpBinary(lutFilePath.c_str(), yuvlut, lutWidth * lutHeight * 3 / 2);
            delete[] yuvlut;
#endif
        for (size_t j = 0; j < width * height * 4; j += 4) {
            auto r = rgba[j];
            auto g = rgba[j + 1];
            auto b = rgba[j + 2];
            int b_div_4 = b / 4;
            int g_div_4 = g / 4;
            int r_div_4 = r / 4;
            int b_div_4_mod_8 = b_div_4 % 8;
            size_t lutIndex = ((b_div_4 / 8 * 64 + g_div_4) * 512 + (b_div_4_mod_8 * 64 + r_div_4)) * 4;
            auto lutPixel = lutTables[filterTag] + lutIndex;
            renderedRgba[j] = lutPixel[0]; // r
            renderedRgba[j + 1] = lutPixel[1]; //g
            renderedRgba[j + 2] = lutPixel[2]; //b
            renderedRgba[j + 3] = rgba[j]; //a
        }
    } else {
        LOGE("unsupported filter tag: %d", filterTag);
    }
    
}

bool FilterManager::recvApplyFilterEffectToJpeg(FilterManager::ApplyFilterEffectMsg *msg) {
    auto jpegBytes = static_cast<uint8_t *>(msg->data);;
    size_t jpegByteSize = msg->dataSize;
    // int filterTag = msg->filterTag;
    cv::Mat jpegMat = cv::imdecode(cv::_InputArray(jpegBytes, jpegByteSize), cv::IMREAD_COLOR);
    if (jpegMat.empty()) {
        LOGD("failed to decode from jpeg image to cv::mat");
        return false;
    }
    int width = jpegMat.cols;
    int height = jpegMat.rows;
#ifdef TEST
    jint width = env->CallIntMethod(jpegImage, env->GetMethodID(imageClass, "getWidth", "()I"));
    jint height = env->CallIntMethod(jpegImage, env->GetMethodID(imageClass, "getHeight", "()I"));
    std::string jpegFilePath = std::format("{}/converted_{}x{}_{}.jpeg", Util::cachePath, width, height,
                Util::getCurrentTimestampMs());
    LOGD("dump cv::mat from jpeg image to %s", jpegFilePath.c_str());
    cv::imwrite(jpegFilePath, jpegMat);
    cv::Mat yuvMa;
#endif
    cv::Mat yuvMat;
    // todo: use libjpeg to convert jpeg to yuv420sp, 替换掉opencv的cvtColor
    cv::cvtColor(jpegMat, yuvMat, cv::COLOR_BGR2YUV_I420);
    // after cvtColor, yuvMat is I420, yuvMat.cols == width, yuvMat.rows == height * 3 / 2
    // I420 yyyy...uu..vv.. y,u,v分别存储，存储为所有的y, 所有的u，所有的v
    // YV12 yyyy...vv..uu.. y,u,v分别存储
    auto yuvBuffer = Color::YuvBuffer(width, height);
    // copy y
    memcpy(yuvBuffer.data, yuvMat.data, yuvBuffer.width * yuvBuffer.height);
    int uIndex = width * height;
    for (int i = width * height; i < width * height * 5 / 4; i++) {
        yuvBuffer.data[uIndex] = yuvMat.data[i];
        uIndex += 2;
    }
    int vIndex = width * height + 1;
    for (int i = width * height * 5 / 4; i < width * height * 3 / 2; i++) {
        yuvBuffer.data[vIndex] = yuvMat.data[i];
        vIndex += 2;
    }
#ifdef TEST
    std::string yuvFilePath = std::format("{}/jpeg_yuv_{}x{}_{}.420sp.yuv", Util::cachePath, width, height,
                                          Util::getCurrentTimestampMs());
    LOGD("dump jpeg yuv to %s", yuvFilePath.c_str());
    Util::dumpBinary(yuvFilePath.c_str(), yuvBuffer.data, yuvBuffer.width * yuvBuffer.height * 3 / 2);
#endif
    auto rgba = new uint8_t[width * height * 4];
    yuvBuffer.convertToRGBA8888(rgba);
    auto renderedRgba = new uint8_t[width * height * 4];
    renderFilterEffect(msg->filterTag, rgba, width, height, renderedRgba);
    auto renderedJpegMat = cv::Mat(height, width, CV_8UC4, renderedRgba);
    std::string renderedJpegFilePath = std::format("{}/rendered_{}x{}_{}.jpeg", Util::cachePath, width, height,
                                                   Util::getCurrentTimestampMs());
    LOGD("dump rendered jpeg to %s", renderedJpegFilePath.c_str());
    cv::imwrite(renderedJpegFilePath, renderedJpegMat);
    Listener::onProcessed(msg->jobId, Listener_type::Listener_type_FILTER_EFFECT_APPLIED_TO_JPEG, renderedJpegFilePath);
    delete msg;
    return true;
}

bool FilterManager::recvClearThumbnails() {
    LOGD();
    // 新的thread，必须要要获取env
    JNIEnv *env;
    Util::get_env(&env);
    for (auto &bitmap: thumbnailBitmaps) {
        bitmap.destroy(env);
    }
    for (auto &lut: lutTables) {
        delete[] lut.second;
    }
    lutTables.clear();
    return true;
}

bool FilterManager::quit() {
    LOGD();
    Looper::quit();
    return true;
}