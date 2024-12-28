//
// Created by tainzhi on 12/11/24.
// Email: qfq61@qq.com
//

#ifndef TCAMERA_FILTER_H
#define TCAMERA_FILTER_H

#include <jni.h>
#include <vector>
#include <opencv2/core/mat.hpp>
#include <opencv2/imgproc.hpp>
#include <opencv2/core/mat.hpp>
#include <opencv2/imgcodecs.hpp>
#include "bitmap.h"
#include "util.h"
#include "looper.h"




class FilterManager: public Looper {
public:
    bool configureThumbnails(JNIEnv *env, jint thumbnail_width, jint thumbnail_height,
            jobject filter_names, jobject filter_tags, jobject filter_thumbnail_bitmaps, jobject lut_bitmaps);
    bool processThumbnails(cv::Mat *yuvMat);
    bool clearThumbnails(JNIEnv *env);
    
    enum kMessage {
        kMessage_ProcessThumbnails = 1,
        kMessage_Clear = 2,
    };
private:
    void handle(int what, void *data) override;
    void process(cv::Mat *mat);
    int thumbnail_width;
    int thumbnail_height;
    std::vector<std::string> filterNames;
    std::vector<int> filterTags;
    std::vector<Bitmap> thumbnailBitmaps;
    std::vector<Bitmap> lutBitmaps;
};


#endif //TCAMERA_FILTER_H
