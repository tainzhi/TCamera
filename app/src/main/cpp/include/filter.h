//
// Created by tainzhi on 12/11/24.
// Email: qfq61@qq.com
//

#ifndef TCAMERA_FILTER_H
#define TCAMERA_FILTER_H

#include <jni.h>
#include <vector>
#include "bitmap.h"
#include "util.h"
#include "looper.h"
#include "yuv.h"




class FilterManager: public Looper {
public:
    ~FilterManager();
    bool configureThumbnails(JNIEnv *env, jint thumbnail_width, jint thumbnail_height,
            jobject filter_names, jobject filter_tags, jobject filter_thumbnail_bitmaps, jobject lut_bitmaps);
    bool processThumbnails(YuvBuffer *yuvBuffer, int orientation);
    bool clearThumbnails(JNIEnv *env);
private:
    void handle(int what, void *data) override;
    void process(YuvBuffer * yuvBuffer);
    int thumbnailWidth;
    int thumbnailHeight;
    int orientation; // image orientation, 0, 90, 180, 270, 也就是thumbnail需要旋转的角度
    std::vector<std::string> filterNames;
    std::vector<int> filterTags;
    std::vector<Bitmap> thumbnailBitmaps;
    std::vector<uint8_t*> lutTables;
    int lutWidth, lutHeight;
    
    enum kMessage {
        kMessage_ProcessThumbnails = 1,
        kMessage_Clear = 2,
    };
};


#endif //TCAMERA_FILTER_H
