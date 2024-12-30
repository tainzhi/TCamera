//
// Created by tainzhi on 12/30/24.
// Email: qfq61@qq.com
//

#ifndef TCAMERA_YUV_H
#define TCAMERA_YUV_H

#include <cstring>
#include <malloc.h>
#include <algorithm>
#include "util.h"

#define TAG NativeYuv

typedef struct YuvBuffer {
    
    uint8_t *data;
    int width;
    int height;
    
    // int y_size;
    // int uv_size;
    // 默认 YUV420sp, that is NV12
    YuvBuffer(uint8_t *y, uint8_t *uv, int width, int height): height(height), width(width) {
        this->data = (uint8_t *) malloc(width * height * 3 / 2);
        memcpy(this->data, y, width * height);
        memcpy(this->data + width * height, uv, width * height / 2);
    }
    
    YuvBuffer(uint8_t *yuv, int width, int height): height(height), width(width) {
        this->data = (uint8_t *) malloc(width * height * 3 / 2);
        memcpy(this->data, yuv, width * height * 3 / 2);
    }
    
    YuvBuffer(int width, int height): height(height), width(width) {
        this->data = (uint8_t *) malloc(width * height * 3 / 2);
    }
    
    YuvBuffer(): data(nullptr), height(0), width(0)  {
    }
    
    YuvBuffer(YuvBuffer & yuv) = delete;
    
    YuvBuffer(YuvBuffer &&yuv) {
        this->data = yuv.data;
        this->height = yuv.height;
        this->width = yuv.width;
        yuv.data = nullptr;
    }
    
    YuvBuffer &operator=(YuvBuffer &&yuv) {
        if (this != &yuv) {
            this->data = yuv.data;
            this->height = yuv.height;
            this->width = yuv.width;
            yuv.data = nullptr;
        }
        return *this;
    }
    
    ~YuvBuffer() {
        if (data != nullptr)
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
    
    void rotate(YuvBuffer &dstYuv, int rotation) {
        int pos = 0;
        int k = 0;
        switch(rotation) {
            case 0:
                dstYuv = std::move(*this);
                break;
            case 90:
                dstYuv.width = height;
                dstYuv.height = width;
                dstYuv.data = (uint8_t *) malloc(dstYuv.width * dstYuv.height * 3 / 2);
                k = 0;
                for (int i = 0; i < width; i++) {
                    for (int j = height - 1; j >= 0; j--) {
                        dstYuv.data[k++] = data[j * width + i];
                    }
                }
                pos = width * height;
                for (int i = 0; i < width - 1; i += 2) {
                    for (int j = height / 2 - 1; j >= 0; j--) {
                        dstYuv.data[k++] = data[pos + j * width + i];
                        dstYuv.data[k++] = data[pos + j * width + i + 1];
                    }
                }
                break;
            case 180:
                // rotate 180
                dstYuv.width = width;
                dstYuv.height = height;
                dstYuv.data = (uint8_t *) malloc(dstYuv.width * dstYuv.height * 3 / 2);
                k = width * height - 1;
                pos = 0;
                while (k >= 0) {
                    dstYuv.data[pos++] = data[k--];
                }
                k = width * height * 3 / 2 - 2;
                while (pos < width * height * 3 / 2) {
                    dstYuv.data[pos++] = data[k];
                    dstYuv.data[pos++] = data[k + 1];
                    k -= 2;
                }
                break;
            case 270:
                // rotate 270
                dstYuv.width = height;
                dstYuv.height = width;
                dstYuv.data = (uint8_t *) malloc(dstYuv.width * dstYuv.height * 3 / 2);
                k = 0;
                for (int i = width - 1; i >= 0; i--) {
                    for (int j = 0; j < height; j++) {
                        dstYuv.data[k++] = data[j * width + i];
                    }
                }
                pos = width * height;
                for (int i = width - 2; i >= 0; i -= 2) {
                    for (int j = 0; j < height / 2; j++) {
                        dstYuv.data[k++] = data[pos + j * width + i];
                        dstYuv.data[k++] = data[pos + j * width + i + 1];
                    }
                }
                break;
            default:
                // LOGE("rotation %d not supported, only support 0/90/180/270", rotation);
                break;
                
        }
    }
};

#endif //TCAMERA_YUV_H
