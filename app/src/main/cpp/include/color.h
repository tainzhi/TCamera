//
// Created by tainzhi on 12/30/24.
// Email: qfq61@qq.com
//

#ifndef TCAMERA_COLOR_H
#define TCAMERA_COLOR_H

#include <cstring>
#include <malloc.h>
#include <algorithm>
#include <arm_neon.h>
#include "util.h"

namespace Color {
    
    // todo: 优化 硬件加速
    typedef struct YuvBuffer {
        
        uint8_t *data;
        int width;
        int height;
        
        // int y_size;
        // int uv_size;
        // 默认 YUV420sp, that is NV12
        YuvBuffer(uint8_t *y, uint8_t *uv, int width, int height) : width(width), height(height) {
            this->data = (uint8_t *) malloc(width * height * 3 / 2);
            memcpy(this->data, y, width * height);
            memcpy(this->data + width * height, uv, width * height / 2);
        }
        
        YuvBuffer(uint8_t *yuv, int width, int height) : width(width), height(height) {
            this->data = (uint8_t *) malloc(width * height * 3 / 2);
            memcpy(this->data, yuv, width * height * 3 / 2);
        }
        
        YuvBuffer(int width, int height) : width(width), height(height) {
            this->data = (uint8_t *) malloc(width * height * 3 / 2);
        }
        
        YuvBuffer() : data(nullptr), width(0), height(0) {
        }
        
        YuvBuffer(YuvBuffer &yuv) = delete;
        
        YuvBuffer(YuvBuffer &&yuv) {
            this->data = yuv.data;
            this->width = yuv.width;
            this->height = yuv.height;
            yuv.data = nullptr;
        }
        
        YuvBuffer &operator=(YuvBuffer &&yuv) {
            if (this != &yuv) {
                this->data = yuv.data;
                this->width = yuv.width;
                this->height = yuv.height;
                yuv.data = nullptr;
            }
            return *this;
        }
        
        ~YuvBuffer() {
            if (data != nullptr)
                free(data);
        }
        
        // void convertToRGBA8888(uint8_t *rgba) {
        //     int yIndex = 0;
        //     int uvIndex = width * height;
        //     int rgbaIndex = 0;
        //     for (int y = 0; y < height; y++) {
        //         for (int x = 0; x < width; x++) {
        //             int yValue = data[yIndex++];
        //             int uValue = data[uvIndex + (y / 2) * width + (x / 2) * 2];
        //             int vValue = data[uvIndex + (y / 2) * width + (x / 2) * 2 + 1];
        //             int rValue = yValue + (1.370705 * (vValue - 128));
        //             int gValue = yValue - (0.698001 * (vValue - 128)) - (0.337633 * (uValue - 128));
        //             int bValue = yValue + (1.732446 * (uValue - 128));
        //             rgba[rgbaIndex++] = (uint8_t) std::max(0, std::min(255, rValue));
        //             rgba[rgbaIndex++] = (uint8_t) std::max(0, std::min(255, gValue));
        //             rgba[rgbaIndex++] = (uint8_t) std::max(0, std::min(255, bValue));
        //             rgba[rgbaIndex++] = 255; // Alpha channel is set to 255 (fully opaque)
        //         }
        //     }
        // }
        
        void convertToRGBA8888(uint8_t *rgba) {
            int yIndex = 0;
            int uvIndex = width * height;
            int rgbaIndex = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int yValue = data[yIndex++];
                    int uValue = data[uvIndex + (y / 2) * width + (x / 2) * 2];
                    int vValue = data[uvIndex + (y / 2) * width + (x / 2) * 2 + 1];
                    int rValue = yValue + (1.402 * (vValue - 128));
                    int gValue = yValue - (0.344136 * (uValue - 128)) - (0.714136 * (vValue - 128));
                    int bValue = yValue + (1.772 * (uValue - 128));
                    rgba[rgbaIndex++] = (uint8_t) std::max(0, std::min(255, rValue));
                    rgba[rgbaIndex++] = (uint8_t) std::max(0, std::min(255, gValue));
                    rgba[rgbaIndex++] = (uint8_t) std::max(0, std::min(255, bValue));
                    rgba[rgbaIndex++] = 255; // Alpha channel is set to 255 (fully opaque)
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
                memcpy(dstYuv.data + (y - startY) * dstWidth, data + y * width + startX, dstWidth);
            }
            int dstUvOffset = dstWidth * dstHeight;
            int uvOffset = width * height;
            // for (int y = startY / 2; y < startY / 2 + dstHeight / 2; y++) {
            //     memcpy(dstYuv.data + dstUvOffset + (y - startY / 2) * dstWidth, data + uvOffset + y * width + startX,
            //            dstWidth);
            // }
            for (int y = startY / 2; y < startY / 2 + dstHeight / 2; y++) {
                for (int x = startX / 2; x < startX / 2 + dstWidth / 2; x++) {
                    dstYuv.data[dstUvOffset + (y - startY / 2) * dstWidth + (x - startX / 2) * 2] = data[uvOffset +
                                                                                                         y * width +
                                                                                                         x * 2];
                    dstYuv.data[dstUvOffset + (y - startY / 2) * dstWidth + (x - startX / 2) * 2 + 1] = data[uvOffset +
                                                                                                             y * width +
                                                                                                             x * 2 + 1];
                }
            }
        }
        
        void rotate(YuvBuffer &dstYuv, int rotation) {
            int pos = 0;
            int k = 0;
            switch (rotation) {
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
                    break;
                
            }
        }
    } YuvBuffer;
    
    
    typedef struct vec4 {
        double x, y, z, w;
    } vec4;
    
    // /**
    //  * reference: https://gist.github.com/emanuel-sanabria-developer/5793377
    //  * @param rgba 4个字节，32位的像素rgba的地址
    //  * @param hsl 单像素的hsl(a)的地址
    //  */
    // static void rgba2hsl(uint8_t *rgba, float *hsl) {
    //     float r = static_cast<float>(rgba[0]) / 255.0f;
    //     float g = static_cast<float>(rgba[1]) / 255.0f;
    //     float b = static_cast<float>(rgba[2]) / 255.0f;
    //     float a = static_cast<float>(rgba[3]) / 255.0f;
    //     float max = MAX(MAX(r, g), b);
    //     float min = MIN(MIN(r, g), b);
    //     float h, s, l;
    //
    //     h = s = l = (max + min) / 2;
    //
    //     if (max == min) {
    //         h = s = 0; // achromatic
    //     } else {
    //         float d = max - min;
    //         s = (l > 0.5) ? d / (2 - max - min) : d / (max + min);
    //
    //         if (max == r) {
    //             h = (g - b) / d + (g < b ? 6 : 0);
    //         } else if (max == g) {
    //             h = (b - r) / d + 2;
    //         } else if (max == b) {
    //             h = (r - g) / d + 4;
    //         }
    //
    //         h /= 6;
    //     }
    //     hsl[0] = h;
    //     hsl[1] = s;
    //     hsl[2] = l;
    //     hsl[3] = a;
    // }
    //
    // static float hue2rgb(float p, float q, float t) {
    //     if (t < 0)
    //         t += 1;
    //     if (t > 1)
    //         t -= 1;
    //     if (t < 1./6)
    //         return p + (q - p) * 6 * t;
    //     if (t < 1./2)
    //         return q;
    //     if (t < 2./3)
    //         return p + (q - p) * (2./3 - t) * 6;
    //
    //     return p;
    // }
    //
    // static void hsl2rgba(float *hsl, uint8_t *rgba) {
    //     float r, g, b, a;
    //     float h = hsl[0];
    //     float s = hsl[1];
    //     float l = hsl[2];
    //     a = hsl[3];
    //     if(0 == s) {
    //         r = g = b = l; // achromatic
    //     }
    //     else {
    //         float q = l < 0.5 ? l * (1 + s) : l + s - l * s;
    //         float p = 2 * l - q;
    //         r = hue2rgb(p, q, h + 1./3) * 255;
    //         g = hue2rgb(p, q, h) * 255;
    //         b = hue2rgb(p, q, h - 1./3) * 255;
    //     }
    //     rgba[0] = static_cast<uint8_t>(r * 255);
    //     rgba[1] = static_cast<uint8_t>(g * 255);
    //     rgba[2] = static_cast<uint8_t>(b * 255);
    //     rgba[3] = static_cast<uint8_t>(a * 255);
    // }
    
    static void rgba2hsl(uint8_t *rgba, float *hsl) {
        float r = rgba[0] / 255.0f;
        float g = rgba[1] / 255.0f;
        float b = rgba[2] / 255.0f;
        float a = rgba[3] / 255.0f;
        vec4 k{0, -1.0 / 3, 2.0 / 3, -1.0};
        vec4 p = b < g ? vec4(g, b, k.x, k.y) : vec4(b, g, k.w, k.z);
        vec4 q = p.x < r ? vec4(r, p.y, p.z, p.x) : vec4(p.x, p.y, p.w, r);
        float d = q.x - std::min(q.w, q.y);
        float e = 1.0e-10;
        float h = std::abs(q.z + (q.w - q.y) / (6.0 * d + e));
        float s = d / (q.x + e);
        float l = q.x;
        hsl[0] = h;
        hsl[1] = s;
        hsl[2] = l;
        hsl[3] = a;
    }
    
    static void hsl2rgba(float *hsl, uint8_t *rgba) {
        float h = hsl[0];
        float s = hsl[1];
        float l = hsl[2];
        float a = hsl[3];
        
        vec4 k{1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0};
        vec4 p{std::abs((h + k.x - std::floor(h + k.x)) * 6.0 - k.w),
               std::abs((h + k.y - std::floor(h + k.y)) * 6.0 - k.w),
               std::abs((h + k.z - std::floor(h + k.z)) * 6.0 - k.w), 0};
        vec4 q{std::clamp(p.x - k.x, 0.0, 1.0), std::clamp(p.y - k.x, 0.0, 1.0), std::clamp(p.z - k.x, 0.0, 1.0), 0
        
        };
        rgba[0] = l * ((1 - s) * k.x + s * q.x) * 255;
        rgba[1] = l * ((1 - s) * k.x + s * q.y) * 255;
        rgba[2] = l * ((1 - s) * k.x + s * q.z) * 255;
        rgba[3] = a * 255;
    }
    
    static void rgba2yuv(uint8_t *rgba, int width, int height, uint8_t *yuv) {
        int yIndex = 0;
        int uvIndex = width * height;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgbaIndex = (y * width + x) * 4;
                uint8_t r = rgba[rgbaIndex];
                uint8_t g = rgba[rgbaIndex + 1];
                uint8_t b = rgba[rgbaIndex + 2];
                // uint8_t a = rgba[rgbaIndex + 3];
                
                // Calculate Y
                int yValue = static_cast<int>(0.299 * r + 0.587 * g + 0.114 * b);
                yValue = std::max(0, std::min(255, yValue));
                yuv[yIndex++] = static_cast<uint8_t>(yValue);
                
                // Calculate U and V for every 2x2 block
                if (x % 2 == 0 && y % 2 == 0) {
                    int uValue = static_cast<int>(-0.168736 * r - 0.331264 * g + 0.5 * b + 128);
                    int vValue = static_cast<int>(0.5 * r - 0.418688 * g - 0.081312 * b + 128);
                    uValue = std::max(0, std::min(255, uValue));
                    vValue = std::max(0, std::min(255, vValue));
                    yuv[uvIndex++] = static_cast<uint8_t>(uValue);
                    yuv[uvIndex++] = static_cast<uint8_t>(vValue);
                }
            }
        }
    }
}

#endif //TCAMERA_COLOR_H
