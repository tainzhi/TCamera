//
// Created by tainzhi
// Email: qfq61@qq.com
//
#ifndef TCAMERA_CL_PROCESSOR_H
#define TCAMERA_CL_PROCESSOR_H

#include <CL/cl.h>
#include <unordered_map>
#include "util.h"
#include "processor.h"

/**
 * @brief 定义一个可变参数宏 OPENCL_SOURCE，用于将传入的参数转换为字符串。
 *
 * 在 OpenCL 编程中，内核代码通常需要以字符串的形式传递给 OpenCL 运行时。
 * 此宏可以方便地将多行的 OpenCL 内核代码转换为一个字符串。
 *
 * @param ... 可变参数，代表任意数量和类型的参数，通常为多行 OpenCL 内核代码。
 * @return 一个包含所有传入参数内容的字符串常量。
 */
#define OPENCL_SOURCE(...) #__VA_ARGS__

struct ClKernelInfo {
    const char *kernelName;
    const char *programSource;
    cl_program clProgram;
    cl_kernel clKernel;
};


enum class FilterTag;

class ClProcessor : public BaseProcessor {

private:
    cl_context clContext = nullptr;
    cl_command_queue clCommandQueue = nullptr;
    cl_device_id deviceId = nullptr;
    cl_mem clInputBuffer = nullptr;
    cl_mem clLutTableInputBuffer = nullptr;
    cl_mem clOutputBuffer = nullptr;
    size_t bufferSize = 0;
    std::unordered_map<FilterTag, ClKernelInfo> kernelInfoMap;
    
    // @formatter:off
    [[nodiscard]] const char *greyProgramSource() const {
        return OPENCL_SOURCE(__kernel void grey(__global const uchar *rgba,
                                                unsigned int width,
                                                unsigned int height,
                                                __global uchar *renderedRgba) {
             int x = get_global_id(0);
             int y = get_global_id(1);
             if (x < width && y < height) {
                 int index = y * width + x;
                 uchar gray = (uchar) (0.299 * rgba[index * 4] + 0.587 * rgba[index * 4 + 1] + 0.114 * rgba[index * 4 + 2]);
                 renderedRgba[index * 4] = gray;
                 renderedRgba[index * 4 + 1] = gray;
                 renderedRgba[index * 4 + 2] = gray;
                 renderedRgba[index * 4 + 3] = rgba[index * 4 + 3];
             }
       });
   };
    
    
    [[nodiscard]] const char *blackWhiteProgramSource() const {
        return OPENCL_SOURCE(__kernel void blackWhite(__global const uchar *rgba,
                                                      unsigned int width,
                                                      unsigned int height,
                                                      __global uchar *renderedRgba) {
            uchar threshold = 0.5 * 255;
            int x = get_global_id(0);
            int y = get_global_id(1);
            if (x < width && y < height) {
                int index = y * width + x;
                uchar mean = (uchar) (0.299 * rgba[index * 4] + 0.587 * rgba[index * 4 + 1] + 0.114 * rgba[index * 4 +
                                                                                                         2]);
                if (mean > threshold) {
                    renderedRgba[index * 4] = 255;
                    renderedRgba[index * 4 + 1] = 255;
                    renderedRgba[index * 4 + 2] = 255;
                } else {
                    renderedRgba[index * 4] = 0;
                    renderedRgba[index * 4 + 1] = 0;
                    renderedRgba[index * 4 + 2] = 0;
                }
                renderedRgba[index * 4 + 3] = rgba[index * 4 + 3];
            }
       });
    };
    
    
    [[nodiscard]] const char *reverseProgramSource() const {
    return OPENCL_SOURCE(__kernel void reverse(__global const uchar *rgba,
                                                   unsigned int width,
                                                   unsigned int height,
                                                   __global uchar *renderedRgba) {
        int x = get_global_id(0);
        int y = get_global_id(1);
        if (x < width && y < height) {
            int index = y * width + x;
            renderedRgba[index * 4] = 255 - rgba[index * 4];
            renderedRgba[index * 4 + 1] = 255 - rgba[index * 4 + 1];
            renderedRgba[index * 4 + 2] = 255 - rgba[index * 4 + 2];
            renderedRgba[index * 4 + 3] = rgba[index * 4 + 3];
        }
        });
    };

    [[nodiscard]] const char *brightnessProgramSource() const {
        return OPENCL_SOURCE(__kernel void brightness(
            __global const uchar *rgba,
            unsigned int width,
            unsigned int height,
            __global uchar *renderedRgba
        ) {
            int x = get_global_id(0);
            int y = get_global_id(1);
            if (x < width && y < height) {
                int index = (y * width + x) * 4;
                float r = rgba[index] / 255.0f;
                float g = rgba[index + 1] / 255.0f;
                float b = rgba[index + 2] / 255.0f;
                float a = rgba[index + 3] / 255.0f;

                // RGBA to HSL
                float k0 = 0.0f;
                float k1 = -1.0f / 3.0f;
                float k2 = 2.0f / 3.0f;
                float k3 = -1.0f;
                float p0, p1, p2, p3;
                if (b < g) {
                    p0 = g;
                    p1 = b;
                    p2 = k0;
                    p3 = k1;
                } else {
                    p0 = b;
                    p1 = g;
                    p2 = k3;
                    p3 = k2;
                }
                float q0, q1, q2, q3;
                if (p0 < r) {
                    q0 = r;
                    q1 = p1;
                    q2 = p2;
                    q3 = p0;
                } else {
                    q0 = p0;
                    q1 = p1;
                    q2 = p3;
                    q3 = r;
                }
                float d = q0 - min(q3, q1);
                float e = 1.0e-10f;
                float h = fabs(q2 + (q3 - q1) / (6.0f * d + e));
                float s = d / (q0 + e);
                float l = q0;

                // Adjust brightness
                l += 0.15f;
                l = clamp(l, 0.0f, 1.0f);

                // HSL to RGBA
                float k4 = 1.0f;
                float k5 = 2.0f / 3.0f;
                float k6 = 1.0f / 3.0f;
                float k7 = 3.0f;
                float p4 = fabs((h + k4 - floor(h + k4)) * 6.0f - k7);
                float p5 = fabs((h + k5 - floor(h + k5)) * 6.0f - k7);
                float p6 = fabs((h + k6 - floor(h + k6)) * 6.0f - k7);
                float q4 = clamp(p4 - k4, 0.0f, 1.0f);
                float q5 = clamp(p5 - k4, 0.0f, 1.0f);
                float q6 = clamp(p6 - k4, 0.0f, 1.0f);

                renderedRgba[index] = (uchar)(l * ((1 - s) * k4 + s * q4) * 255);
                renderedRgba[index + 1] = (uchar)(l * ((1 - s) * k4 + s * q5) * 255);
                renderedRgba[index + 2] = (uchar)(l * ((1 - s) * k4 + s * q6) * 255);
                renderedRgba[index + 3] = (uchar)(a * 255);
            }
        });
    }

    [[nodiscard]] const char *posterizationProgramSource() const {
        return OPENCL_SOURCE(__kernel void posterization(
            __global const uchar *rgba,
            unsigned int width,
            unsigned int height,
            __global uchar *renderedRgba
        ) {
            int x = get_global_id(0);
            int y = get_global_id(1);
            if (x < width && y < height) {
                int index = (y * width + x) * 4;

                // Calculate grey value
                float grey = (float)(rgba[index] * 0.3f + rgba[index + 1] * 0.59f + rgba[index + 2] * 0.11f);

                // Convert RGBA to HSL
                float r = (float)rgba[index] / 255.0f;
                float g = (float)rgba[index + 1] / 255.0f;
                float b = (float)rgba[index + 2] / 255.0f;
                float a = (float)rgba[index + 3] / 255.0f;

                float k0 = 0.0f;
                float k1 = -1.0f / 3.0f;
                float k2 = 2.0f / 3.0f;
                float k3 = -1.0f;
                float p0, p1, p2, p3;
                if (b < g) {
                    p0 = g;
                    p1 = b;
                    p2 = k0;
                    p3 = k1;
                } else {
                    p0 = b;
                    p1 = g;
                    p2 = k3;
                    p3 = k2;
                }
                float q0, q1, q2, q3;
                if (p0 < r) {
                    q0 = r;
                    q1 = p1;
                    q2 = p2;
                    q3 = p0;
                } else {
                    q0 = p0;
                    q1 = p1;
                    q2 = p3;
                    q3 = r;
                }
                float d = q0 - min(q3, q1);
                float e = 1.0e-10f;
                float h = fabs(q2 + (q3 - q1) / (6.0f * d + e));
                float s = d / (q0 + e);
                float l = q0;

                // Adjust HSL based on grey value
                if (grey < 0.3f * 255.0f) {
                    if (h < 0.68f || h > 0.66f) {
                        h = 0.67f;
                    }
                    s += 0.3f;
                } else if (grey > 0.7f * 255.0f) {
                    if (h < 0.18f || h > 0.16f) {
                        h = 0.17f;
                    }
                    s -= 0.3f;
                }

                // Convert HSL back to RGBA
                float k4 = 1.0f;
                float k5 = 2.0f / 3.0f;
                float k6 = 1.0f / 3.0f;
                float k7 = 3.0f;
                float p4 = fabs((h + k4 - floor(h + k4)) * 6.0f - k7);
                float p5 = fabs((h + k5 - floor(h + k5)) * 6.0f - k7);
                float p6 = fabs((h + k6 - floor(h + k6)) * 6.0f - k7);
                float q4 = clamp(p4 - k4, 0.0f, 1.0f);
                float q5 = clamp(p5 - k4, 0.0f, 1.0f);
                float q6 = clamp(p6 - k4, 0.0f, 1.0f);

                renderedRgba[index] = (uchar)(l * ((1 - s) * k4 + s * q4) * 255.0f);
                renderedRgba[index + 1] = (uchar)(l * ((1 - s) * k4 + s * q5) * 255.0f);
                renderedRgba[index + 2] = (uchar)(l * ((1 - s) * k4 + s * q6) * 255.0f);
                renderedRgba[index + 3] = (uchar)(a * 255.0f);
            }
        });
    }
    
    [[nodiscard]] const char *lutFilterProgramSource() const {
        return OPENCL_SOURCE(__kernel void lutFilter(__global const uchar *rgba,
                                                     unsigned int width,
                                                     unsigned int height,
                                                     __global const uchar *lutTable,
                                                     __global uchar *renderedRgba) {
            int x = get_global_id(0);
            int y = get_global_id(1);
            if (x < width && y < height) {
                int index = (y * width + x) * 4;
                uchar r = rgba[index];
                uchar g = rgba[index + 1];
                uchar b = rgba[index + 2];
                int b_div_4 = b / 4;
                int g_div_4 = g / 4;
                int r_div_4 = r / 4;
                int b_div_4_mod_8 = b_div_4 % 8;
                size_t lutIndex = ((b_div_4 / 8 * 64 + g_div_4) * 512 + (b_div_4_mod_8 * 64 + r_div_4)) * 4;
                renderedRgba[index] = lutTable[lutIndex];     // r
                renderedRgba[index + 1] = lutTable[lutIndex + 1]; // g
                renderedRgba[index + 2] = lutTable[lutIndex + 2]; // b
                renderedRgba[index + 3] = rgba[index + 3];    // a
            }
        });
    }
    // @formatter:on
    
    [[nodiscard]] const std::string getBuildOptions() const {
        return "-cl-fast-relaxed-math -DQC_OPT -cl-mad-enable";
    }
    
    cl_int createKernelHelper(cl_program clProgram, const char *kernelName, cl_kernel &clKernel) const;
    
    // 初始化 OpenCL 上下文、程序和内核
    bool initOpenCL();

public:
    // 构造函数中调用初始化方法
    ClProcessor();
    
    void setBufferSize(size_t size);
    
    void process(FilterTag filterTag, uint8_t *rgba, int width, int height, uint8_t *renderedRgba) override;
    
    void process(FilterTag filterTag, uint8_t *rgba, int width, int height, uint8_t *lutTable, int lutTableSize,
                 uint8_t *renderedRgba) override;
    
    [[nodiscard]] bool init();
    void deinit();
    
    ~ClProcessor();
};
#endif //TCAMERA_CL_PROCESSOR_H