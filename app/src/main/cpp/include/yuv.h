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
    
    uint8_t *data;
    int width;
    int height;
    
    // int y_size;
    // int uv_size;
    // 默认 YUV420sp, that is NV12
    YuvBuffer(uint8_t *y, uint8_t *uv, int width, int height) {
        this->data = (uint8_t *) malloc(width * height * 3 / 2);
        memcpy(this->data, y, width * height);
        memcpy(this->data + width * height, uv, width * height / 2);
        // y_size = width * height;
        // uv_size = width * height / 2;
    }
    
    YuvBuffer(int width, int height) {
        this->data = (uint8_t *) malloc(width * height * 3 / 2);
        this->height = height;
        this->width = width;
        // y_size = width * height;
        // uv_size = width * height / 2;
    }
    
    ~YuvBuffer() {
        free(data);
    }
    
    void convertToRGBA8888(uint8_t *rgba) {
        int uvOffset = width * height;
        for (int j = 0; j < height; ++j) {
            for (int i = 0; i < width; ++i) {
                int yIndex = j * width + i;
                int uvIndex = uvOffset + (j / 2) * (width / 2) + (i / 2) * 2;
                
                uint8_t y = this->data[yIndex];
                uint8_t u = this->data[uvIndex] - 128;
                uint8_t v = this->data[uvIndex + 1] - 128;
                
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
        int startX = (width - dstWidth) / 2;
        int startY = (height - dstHeight) / 2;
        for (int y = startY; y < startY + dstHeight; y++) {
            memcpy(dstYuv.data + (y - startY) * dstWidth, data + y * width + startX,
                   dstWidth);
        }
        int uvOffset = width * height;
        int dstUvOffset = dstWidth * dstHeight;
        for (int y = startY / 2; y < startY / 2 + dstHeight / 2; y++) {
            memcpy(dstYuv.data + dstUvOffset + (y - startY / 2) * dstWidth,
                   data + uvOffset + y * width + startX, dstWidth);
        }
    }
};

#endif //TCAMERA_YUV_H
