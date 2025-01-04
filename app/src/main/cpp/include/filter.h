//
// Created by tainzhi on 12/11/24.
// Email: qfq61@qq.com
//

#ifndef TCAMERA_FILTER_H
#define TCAMERA_FILTER_H

#include <jni.h>
#include <vector>
#include <opencv2/core/mat.hpp>
#include "bitmap.h"
#include "util.h"
#include "looper.h"
#include "color.h"




class FilterManager: public Looper {
public:
    ~FilterManager();
    bool configureThumbnails(JNIEnv *env, jint thumbnail_width, jint thumbnail_height,
            jobject filter_names, jobject filter_tags, jobject filter_thumbnail_bitmaps, jobject lut_bitmaps);
    bool processThumbnails(Color::YuvBuffer *yuvBuffer, int orientation, int updateRangeStart, int updateRangeEnd);
    bool applyFilterEffectToJpeg(cv::Mat jpegMat, int filterTag);
    bool clearThumbnails(JNIEnv *env);
private:
    void handle(int what, void *data) override;
    void process(void *msg);
    int thumbnailWidth;
    int thumbnailHeight;
    std::vector<std::string> filterNames;
    std::vector<int> filterTags;
    std::vector<Bitmap> thumbnailBitmaps;
    std::vector<uint8_t*> lutTables;
    int lutWidth, lutHeight;
    
    enum kMessage {
        kMessage_ProcessThumbnails = 1,
        kMessage_Clear = 2,
        kMessage_ApplyFilterEffectToJpeg = 3,
    };
    
    struct ThumbnailMsg {
        void *data;
        int orientation;
        int updateRangeStart;
        int updateRangeEnd;
    };
};


#endif //TCAMERA_FILTER_H
