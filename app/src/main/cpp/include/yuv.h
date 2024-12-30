//
// Created by tainzhi on 12/30/24.
// Email: qfq61@qq.com
//

#ifndef TCAMERA_YUV_H
#define TCAMERA_YUV_H

#include <cstring>
#include <malloc.h>
#include <algorithm>

typedef struct YuvBuffer {
    
    uint8_t *yBuffer;
    uint8_t *uvBuffer;
    int width;
    int height;
    
    // int y_size;
    // int uv_size;
    // 默认 YUV420sp, that is NV12
    YuvBuffer(uint8_t *y, uint8_t *uv, int width, int height) {
        this->yBuffer = (uint8_t *) malloc(width * height);
        memcpy(this->yBuffer, y, width * height);
        this->uvBuffer = (uint8_t *) malloc(width * height / 2);
        memcpy(this->uvBuffer, uv, width * height / 2);
        // y_size = width * height;
        // uv_size = width * height / 2;
    }
    
    YuvBuffer(int width, int height) {
        this->yBuffer = (uint8_t *) malloc(width * height);
        this->uvBuffer = (uint8_t *) malloc(width * height / 2);
        this->height = height;
        this->width = width;
        // y_size = width * height;
        // uv_size = width * height / 2;
    }
    
    ~YuvBuffer() {
        free(yBuffer);
        free(uvBuffer);
    }
    
    void convertToRGBA8888(uint8_t *rgba) {
        for (int j = 0; j < height; ++j) {
            for (int i = 0; i < width; ++i) {
                int yIndex = j * width + i;
                int uvIndex = (j / 2) * (width / 2) + (i / 2) * 2;
                
                uint8_t y = this->yBuffer[yIndex];
                uint8_t u = this->uvBuffer[uvIndex] - 128;
                uint8_t v = this->uvBuffer[uvIndex + 1] - 128;
                
                int r = (int) (y + 1.402 * v);
                int g = (int) (y - 0.34414 * u - 0.71414 * v);
                int b = (int) (y + 1.772 * u);
                
                r = std::max(0, std::min(255, r));
                g = std::max(0, std::min(255, g));
                b = std::max(0, std::min(255, b));
                
                int rgbaIndex = (j * width + i) * 4;
                rgba[rgbaIndex + 0] = (uint8_t) r; // R
                rgba[rgbaIndex + 1] = (uint8_t) g; // G
                rgba[rgbaIndex + 2] = (uint8_t) b; // B
                rgba[rgbaIndex + 3] = 255;        // A
            }
        }
    }
    
    void extractCenter(YuvBuffer &dstYuv) {
        assert(width > dstYuv.width);
        assert(height > dstYuv.height);
        int dstWidth = dstYuv.width;
        int dstHeight = dstYuv.height;
        
        int startX = width / 2 - dstWidth / 2;
        int startY = height / 2 - dstHeight / 2;
        for (int y = 0; y < dstHeight; y++) {
            memcpy(dstYuv.yBuffer + y * dstWidth,
                   yBuffer + (y + startY) * width + startX, dstWidth);
        }
        // -height/4 + dstHeight/4
        for (int y = startY / 2; y < startY /2 + dstHeight / 2; y++) {
            memcpy(dstYuv.uvBuffer +
                   (y - startY / 2) * dstWidth,
                   uvBuffer + y * width + startX / 2, dstWidth);
        }
    }
};

#endif //TCAMERA_YUV_H
