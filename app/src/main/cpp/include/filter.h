//
// Created by tainzhi on 12/11/24.
// Email: qfq61@qq.com
//

#ifndef TCAMERA_FILTER_H
#define TCAMERA_FILTER_H

#include <jni.h>
#include <vector>
#include <opencv2/core/mat.hpp>
#include "opencv2/imgcodecs.hpp"
#include <opencv2/imgproc.hpp>
#include "bitmap.h"
#include "util.h"
#include "looper.h"
#include "color.h"
#include "listener.h"


class FilterManager: public Looper {
public:
    ~FilterManager();
    bool configureThumbnails(JNIEnv *env, jint thumbnail_width, jint thumbnail_height,
            jobject filter_names, jobject filter_tags, jobject filter_thumbnail_bitmaps, jobject lut_bitmaps);
    void sendProcessThumbnails(std::shared_ptr<Color::YuvBuffer> yuvBuffer, int orientation, int updateRangeStart, int
    updateRangeEnd);
    void sendApplyFilterEffectToJpeg(int jobId, int filterTag, uint8_t * jpegBytes, int jpegByteSize);
    void sendClearThumbnails();
    bool quit();
private:
    enum kMessage {
        kMessage_ProcessThumbnails = 1,
        kMessage_ClearThumbnails = 2,
        kMessage_ApplyFilterEffectToJpeg = 3,
    };
    
    struct ThumbnailMsg {
        std::shared_ptr<Color::YuvBuffer> yuvBuffer;
        int orientation;
        int updateRangeStart;
        int updateRangeEnd;
    };
    
    struct ApplyFilterEffectMsg {
        int jobId;
        int filterTag;
        void *data;
        size_t dataSize;
    };
    
    bool recvProcessThumbnails(ThumbnailMsg *msg);
    bool recvApplyFilterEffectToJpeg(ApplyFilterEffectMsg *msg);
    bool recvClearThumbnails();
    void renderFilterEffect(int filterTag, uint8_t * rgba, int width, int height, uint8_t *renderedRgba);
    void handle(int what, void *data) override;
    int thumbnailWidth;
    int thumbnailHeight;
    std::vector<std::string> filterNames;
    std::vector<int> filterTags;
    std::vector<Bitmap> thumbnailBitmaps;
    std::unordered_map<int, uint8_t*> lutTables;
    int lutWidth, lutHeight;
};


#endif //TCAMERA_FILTER_H
