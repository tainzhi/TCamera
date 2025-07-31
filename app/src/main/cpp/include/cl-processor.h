//
// Created by tainzhi
// Email: qfq61@qq.com
//
#ifndef TCAMERA_CL_PROCESSOR_H
#define TCAMERA_CL_PROCESSOR_H

#include <CL/cl.h>
#include "util.h"

#define TAG "CLProcessor"

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


class ClProcessor {

private:
    cl_context clContext = nullptr;
    cl_command_queue clCommandQueue = nullptr;
    cl_device_id deviceId = nullptr;
    cl_program clProgram = nullptr;
    std::unordered_map<std::string, cl_kernel> kernelMap;
    std::string kernelName = "basic";
    
    [[nodiscard]] const char *getProgramSource() const {
        return OPENCL_SOURCE(__kernel void
                                     basic(__global const uchar *rgba, unsigned int width, unsigned int height, __global uchar
                                     *renderedRgba) {
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
                             }
        
        );
    };
    
    [[nodiscard]] const std::string getBuildOptions() const {
        return "-cl-fast-relaxed-math -DQC_OPT -cl-mad-enable";
    }
    
    cl_int createKernelHelper(const char *kernelName, cl_kernel &clKernel) const {
        cl_int clError = CL_SUCCESS;
        clKernel = clCreateKernel(clProgram, kernelName, &clError);
        if (clError != CL_SUCCESS) {
            LOGE("clCreateKernel failed: %d", clError);
        }
        return clError;
    }

public:
    typedef std::function<void(size_t, char * , size_t * )> ClStringFn;
    
    [[nodiscard]] std::string getClString(const ClStringFn &clStringFn) {
        size_t strSize = 0;
        clStringFn(0, nullptr, &strSize);
        std::string str(strSize, 0);
        if (!str.empty()) {
            clStringFn(strSize, str.data(), nullptr);
        }
        return str;
    }
    
    void run(uint8_t *rgba, int width, int height, uint8_t *renderedRgba) {
        cl_int clError = CL_SUCCESS;
        cl_uint numPlatforms = 0;
        cl_platform_id platformArray[10];
        clError = clGetPlatformIDs(0, nullptr, &numPlatforms);
        clError = clGetPlatformIDs(numPlatforms, platformArray, &numPlatforms);
        if (clError == CL_SUCCESS) {
            LOGD("%u platforms found", numPlatforms);
        } else {
            LOGE("clGetPlatformIDs(%i) failed", clError);
        }
        for (auto i = 0; i < numPlatforms; i++) {
            auto version = getClString([&](size_t size, char *str, size_t *len) {
                clGetPlatformInfo(platformArray[i], CL_PLATFORM_VERSION, size, str, len);
            });
            LOGD("found platform version: %s", version.c_str());
        }
        
        cl_uint num_devices;
        clError = clGetDeviceIDs(platformArray[0], CL_DEVICE_TYPE_DEFAULT, 1, &deviceId, &num_devices);
        if (clError == CL_SUCCESS) {
            LOGD("clGetDeviceIDs success, device_id:%u", deviceId);
        } else {
            LOGE("clGetDeviceIDs(%i) failed", clError);
        }
        
        cl_context_properties contextProperties[] = {CL_CONTEXT_PLATFORM, (cl_context_properties) platformArray[0], 0};
        clContext = clCreateContextFromType(contextProperties, CL_DEVICE_TYPE_GPU, nullptr, nullptr, &clError);
        if (clError != CL_SUCCESS) {
            LOGE("creating cl_context with GPU failed: %d", clError);
            clContext = clCreateContextFromType(contextProperties, CL_DEVICE_TYPE_CPU, nullptr, nullptr, &clError);
            if (clError != CL_SUCCESS) {
                LOGE("creating cl_context with CPU failed: %d", clError);
            }
        }
        auto temp = (size_t)
        nullptr;
        clError = clGetContextInfo(clContext, CL_CONTEXT_DEVICES, sizeof(cl_device_id), &deviceId, &temp);
        if (clError != CL_SUCCESS) {
            LOGE("clGetContextInfo failed: %d", clError);
        }
        clCommandQueue = clCreateCommandQueue(clContext, deviceId, 0, &clError);
        if (clError != CL_SUCCESS) {
            LOGE("clCreateCommandQueue failed: %d", clError);
            clError = clReleaseContext(clContext);
            if (clError != CL_SUCCESS) {
                LOGE("clReleaseContext failed: %d", clError);
            }
        }
        
        auto clProgramSrc = getProgramSource();
        auto clProgramSrcLen = strlen(clProgramSrc);
        clProgram = clCreateProgramWithSource(clContext, 1, (const char **) &clProgramSrc, &clProgramSrcLen, &clError);
        if (clError != CL_SUCCESS) {
            LOGE("clCreateProgramWithSource failed: %d", clError);
        }
        auto buildOptions = getBuildOptions();
        clError = clBuildProgram(clProgram, 1, &deviceId, buildOptions.c_str(), nullptr, nullptr);
        if (clError != CL_SUCCESS) {
            auto log = getClString([&](size_t size, char *str, size_t *len) {
                clGetProgramBuildInfo(clProgram, deviceId, CL_PROGRAM_BUILD_LOG, size, str, len);
            });
            LOGE("clBuildProgram failed: %d, %s", clError, log.c_str());
        }
        
        cl_kernel clKernel;
        clError = createKernelHelper(kernelName.c_str(), clKernel);
        if (clError != CL_SUCCESS) {
            LOGE("clCreateKernel failed: %d", clError);
        }
        
        //create buffer
        auto bufferSize = width * height * 4;
        cl_mem clInputBuffer = clCreateBuffer(clContext, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR, bufferSize, rgba,
                                              &clError);
        if (clError != CL_SUCCESS) {
            LOGE("clCreateBuffer input failed: %d", clError);
        }
        cl_mem clOutputBuffer = clCreateBuffer(clContext, CL_MEM_WRITE_ONLY, bufferSize, nullptr, &clError);
        if (clError != CL_SUCCESS) {
            LOGE("clCreateBuffer output failed: %d", clError);
        }
        
        clSetKernelArg(clKernel, 0, sizeof(cl_mem), &clInputBuffer);
        clSetKernelArg(clKernel, 1, sizeof(cl_uint), &width);
        clSetKernelArg(clKernel, 2, sizeof(cl_uint), &height);
        clSetKernelArg(clKernel, 3, sizeof(cl_mem), &clOutputBuffer);
        
        size_t globalWorkSize[2] = {static_cast<size_t>(width), static_cast<size_t>(height)};
        clEnqueueNDRangeKernel(clCommandQueue, clKernel, 2, nullptr, globalWorkSize, nullptr, 0, nullptr, nullptr);
        
        // waitOnCommandQueue
        clFinish(clCommandQueue);
        
        clError = clEnqueueReadBuffer(clCommandQueue, clOutputBuffer, CL_TRUE, 0, bufferSize, renderedRgba, 0, nullptr,
                                      nullptr);
        if (clError != CL_SUCCESS) {
            LOGE("clEnqueueReadBuffer failed: %d", clError);
        }
        
        if (clInputBuffer) {
            clReleaseMemObject(clInputBuffer);
            clInputBuffer = nullptr;
        }
        if (clOutputBuffer) {
            clReleaseMemObject(clOutputBuffer);
            clOutputBuffer = nullptr;
        }
        deinit();
        
    }
    
    void deinit() {
        if (clCommandQueue) {
            clReleaseCommandQueue(clCommandQueue);
            clCommandQueue = nullptr;
        }
        if (clContext) {
            clReleaseContext(clContext);
            clContext = nullptr;
        }
    }
};


#endif //TCAMERA_CL_PROCESSOR_H