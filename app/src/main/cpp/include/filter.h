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
#include "cl-processor.h"

class Engine;

enum class FilterTag {
    ORIGINAL = 0,
    GREY = 1,
    BLACK_WHITE = 2,
    REVERSE = 3,
    BRIGHTNESS = 4,
    POSTERIZATION = 5,
    // non-lut < 10, lut filter >= 10
    AMATORKA = 10,
    BEAGLE = 11,
    BIRMAN = 12,
    CORGIS = 13,
    HIGH_KEY = 14,
    LABRADOR = 15,
    MAINE = 16,
    MONO = 17,
    PERSIAN = 18,
    POODLE = 19,
    PUG = 20,
    PURITY = 21,
    SHORT_HAIR = 22,
    SIAMESE = 23,
    VERTICAL = 24,
};


class FilterManager: public Looper {
public:
    FilterManager(Engine *engine);
    ~FilterManager();
    bool configureThumbnails(JNIEnv *env, jint thumbnail_width, jint thumbnail_height,
            jobject filter_names, jobject filter_tags, jobject filter_thumbnail_bitmaps, jobject lut_bitmaps);
    void sendProcessThumbnails(std::shared_ptr<Color::YuvBuffer> yuvBuffer, int orientation, int updateRangeStart, int
    updateRangeEnd);
    void sendApplyFilterEffectToHdr(int jobId, int filterTag, Color::YuvBuffer * yuv);
    void sendApplyFilterEffectToJpeg(int jobId, int filterTag, uint8_t * jpegBytes, int jpegByteSize);
    void sendClearThumbnails(int selectedFilterTag);
    bool quit();
private:
    enum kMessage {
        kMessage_ProcessThumbnails = 1,
        kMessage_ClearThumbnails = 2,
        kMessage_ApplyFilterEffectToJpeg = 3,
        kMessage_ApplyFilterEffectToHdr = 4,
    };
    
    struct ThumbnailMsg {
        std::shared_ptr<Color::YuvBuffer> yuvBuffer;
        int orientation;
        int updateRangeStart;
        int updateRangeEnd;
    };
    
    struct ClearThumbnailsMsg {
        int selectedFilterTag;
    };
    
    struct ApplyFilterEffectMsg {
        int jobId;
        int filterTag;
        void *data;
        size_t dataSize;
    };
    
    struct ApplyFilterEffectToHdrMsg {
        int jobId;
        int filterTag;
        Color::YuvBuffer *yuvBuffer;
        ~ApplyFilterEffectToHdrMsg() {
            if (yuvBuffer) {
                delete yuvBuffer;
            }
        }
    };
    
    bool recvProcessThumbnails(ThumbnailMsg *msg);
    bool recvApplyFilterEffectToJpeg(ApplyFilterEffectMsg *msg);
    bool recvApplyFilterEffectToHdr(ApplyFilterEffectToHdrMsg *msg);
    bool recvClearThumbnails(ClearThumbnailsMsg *msg);
    void renderFilterEffect(int filterTag, uint8_t * rgba, int width, int height, uint8_t *renderedRgba);
    void handle(int what, void *data) override;
    void addDropMsg() override;
    
    int thumbnailWidth;
    int thumbnailHeight;
    std::vector<std::string> filterNames;
    std::vector<int> filterTags;
    std::vector<Bitmap> thumbnailBitmaps;
    std::unordered_map<int, uint8_t*> lutTables;
    int lutWidth, lutHeight;
    ClProcessor clProcessor;
};


#endif //TCAMERA_FILTER_H
