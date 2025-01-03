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
            Bitmap::getBitmapData(env, jlutbitmap, lutData, lutWidth, lutHeight);
            this->lutTables.emplace_back(lutData);
        } else {
            this->lutTables.emplace_back(nullptr);
        }
        env->DeleteLocalRef(jthubmnailbitmap);
        env->DeleteLocalRef(jlutbitmap);
    }
    env->DeleteLocalRef(thumbnailBitmapsClass);
    LOGD("end");
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
    assert(yuvBuffer->width >= thumbnailWidth && yuvBuffer->height >= thumbnailHeight);
#ifdef TEST
    std::string yuvFilePath = std::format("{}/yuv_{}x{}_{}.420sp.yuv", Util::cachePath, yuvBuffer->width,
                                                yuvBuffer->height, Util::getCurrentTimestampMs());
    LOGD("save rotate yuv to %s", yuvFilePath.c_str());
    Util::dumpBinary(yuvFilePath.c_str(), yuvBuffer->data, yuvBuffer->width * yuvBuffer->height * 3 / 2);
#endif
    // 从yuvBuffer中截取中心区域的yuv数据
    YuvBuffer centerYuv(thumbnailWidth, thumbnailHeight);
    yuvBuffer->extractCenter(centerYuv);
#ifdef TEST
    std::string centerYuvFilePath = std::format("{}/center_{}x{}_{}.420sp.yuv", Util::cachePath, thumbnailWidth,
                                                thumbnailHeight, Util::getCurrentTimestampMs());
    LOGD("save center yuv to %s", centerYuvFilePath.c_str());
    Util::dumpBinary(centerYuvFilePath.c_str(), centerYuv.data, thumbnailWidth * thumbnailHeight * 3 / 2);
#endif
    // 旋转
    YuvBuffer rotateYuv;
    centerYuv.rotate(rotateYuv, orientation);
#ifdef TEST
    std::string rotateYuvFilePath = std::format("{}/rotate_{}x{}_{}.420sp.yuv", Util::cachePath, thumbnailWidth,
                                                thumbnailHeight, Util::getCurrentTimestampMs());
    LOGD("save rotate yuv to %s", rotateYuvFilePath.c_str());
    Util::dumpBinary(rotateYuvFilePath.c_str(), rotateYuv.data, thumbnailWidth * thumbnailHeight * 3 / 2);
#endif
    
    // 生成rgba数据
    uint8_t *rgba = new uint8_t[thumbnailWidth * thumbnailHeight * 4];
    rotateYuv.convertToRGBA8888(rgba);
    uint8_t *rendered_rgba = new uint8_t[thumbnailWidth * thumbnailHeight * 4];
    float * hsl = new float[thumbnailWidth * thumbnailHeight * 4];
    
    JNIEnv *env;
    Util::get_env(&env);
    
    for (size_t i = 0; i < thumbnailBitmaps.size(); i++) {
        if (filterTags[i] == 0) {
            thumbnailBitmaps[i].render(env, rgba, thumbnailWidth * thumbnailHeight * 4);
        } else if (filterTags[i] == 1) {
            // grey
            for (size_t j = 0; j < thumbnailWidth * thumbnailHeight * 4; j += 4) {
                uint8_t wm = rgba[j] * 0.3 + rgba[j + 1] * 0.59 + rgba[j + 2] * 0.11;
                rendered_rgba[j] = wm;
                rendered_rgba[j + 1] = wm;
                rendered_rgba[j + 2] = wm;
                rendered_rgba[j + 3] = rgba[j + 3];
            }
            thumbnailBitmaps[i].render(env, rendered_rgba, thumbnailWidth * thumbnailHeight * 4);
        } else if (filterTags[i] == 2) {
            // black and white
            uint8_t threshold = 0.5 * 255;
            uint8_t mean;
            for (size_t j = 0; j < thumbnailWidth * thumbnailHeight * 4; j += 4) {
                mean = (rgba[j] + rgba[j + 1] + rgba[j + 2]) / 3.0;
                if (mean > threshold) {
                    rendered_rgba[j] = 255;
                    rendered_rgba[j + 1] = 255;
                    rendered_rgba[j + 2] = 255;
                    rendered_rgba[j + 3] = rgba[j + 3];
                } else {
                    rendered_rgba[j] = 0;
                    rendered_rgba[j + 1] = 0;
                    rendered_rgba[j + 2] = 0;
                    rendered_rgba[j + 3] = rgba[j + 3];
                }
            }
            thumbnailBitmaps[i].render(env, rendered_rgba, thumbnailWidth * thumbnailHeight * 4);
        } else if (filterTags[i] == 3) {
            // reverse
            for (size_t j = 0; j < thumbnailWidth * thumbnailHeight * 4; j += 4) {
                rendered_rgba[j] = 255 - rgba[j];
                rendered_rgba[j + 1] = 255 - rgba[j + 1];
                rendered_rgba[j + 2] = 255 - rgba[j + 2];
                rendered_rgba[j + 3] = rgba[j + 3];
            }
            thumbnailBitmaps[i].render(env, rendered_rgba, thumbnailWidth * thumbnailHeight * 4);
        } else if (filterTags[i] == 4) {
            // light
            for (size_t j = 0; j < thumbnailWidth * thumbnailHeight * 4; j += 4) {
                Color::rgba2hsl(rgba + j, hsl + j);
                // hsl[j + 2] += 0.15;
                Color::hsl2rgba(hsl + j, rendered_rgba + j);
            }
            thumbnailBitmaps[i].render(env, rendered_rgba, thumbnailWidth * thumbnailHeight * 4);
        } else if (filterTags[i] == 5) {
            // posterization
            for (size_t j = 0; j < thumbnailWidth * thumbnailHeight * 4; j+=4 ) {
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
                Color::hsl2rgba(hsl + j, rendered_rgba + j);
            }
            thumbnailBitmaps[i].render(env, rendered_rgba, thumbnailWidth * thumbnailHeight * 4);
        } else if (filterTags[i] >=10) {
            uint8_t *yuvlut = new uint8_t[lutWidth * lutHeight * 3 / 2];
            Color::argb2yuv(this->lutTables[i], lutWidth, lutHeight , yuvlut);
            std::string lutFilePath = std::format("{}/lut_{}_{}x{}.420sp.yuv", Util::cachePath, filterTags[i],
                                                  lutWidth,
                                                  lutHeight);
            Util::dumpBinary(lutFilePath.c_str(), yuvlut, lutWidth * lutHeight * 3 / 2);
        } else {
            LOGE("unsupported filter tag: %d", filterTags[i]);
        }
    }
    delete[] rgba;
    delete[] rendered_rgba;
    delete[] hsl;
    delete yuvBuffer;
}

bool FilterManager::clearThumbnails(JNIEnv *env) {
    LOGD();
    for (auto &bitmap: thumbnailBitmaps) {
        bitmap.destroy(env);
    }
    for (auto &lut: lutTables) {
        if (lut != nullptr) {
            delete[] lut;
        }
    }
    return true;
}