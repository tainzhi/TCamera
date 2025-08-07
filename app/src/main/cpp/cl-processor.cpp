//
// Created by tainzhi on 2025/8/7.
//

#include "cl-processor.h"
#include "util.h"
#include "filter.h"

#define TAG "CLProcessor"

cl_int ClProcessor::createKernelHelper(cl_program clProgram, const char *kernelName, cl_kernel &clKernel) const {
    cl_int clError = CL_SUCCESS;
    clKernel = clCreateKernel(clProgram, kernelName, &clError);
    if (clError != CL_SUCCESS) {
        LOGE("clCreateKernel failed: %d", clError);
    }
    return clError;
}

bool ClProcessor::initOpenCL() {
    kernelInfoMap = {{FilterTag::GREY,        {"grey",       greyProgramSource(),       nullptr, nullptr}},
                     {FilterTag::BLACK_WHITE, {"blackWhite", blackWhiteProgramSource(), nullptr, nullptr}},
                     {FilterTag::REVERSE,       {"reverse",       reverseProgramSource(),       nullptr, nullptr}},
                     {FilterTag::BRIGHTNESS,    {"brightness",    brightnessProgramSource(),    nullptr, nullptr}},
                     {FilterTag::POSTERIZATION, {"posterization", posterizationProgramSource(), nullptr, nullptr}},
                     {FilterTag::LUT_FILTER,    {"lutFilter",     lutFilterProgramSource(),     nullptr, nullptr}},};
    
    cl_int clError = CL_SUCCESS;
    cl_uint numPlatforms = 0;
    cl_platform_id platformArray[10];
    clError = clGetPlatformIDs(0, nullptr, &numPlatforms);
    clError = clGetPlatformIDs(numPlatforms, platformArray, &numPlatforms);
    if (clError != CL_SUCCESS) {
        LOGE("clGetPlatformIDs(%i) failed", clError);
        return false;
    }
    LOGD("%u platforms found", numPlatforms);
    
    for (auto i = 0; i < numPlatforms; i++) {
        size_t strSize = 0;
        clGetPlatformInfo(platformArray[i], CL_PLATFORM_NAME, 0, nullptr, &strSize);
        std::string version(strSize, 0);
        if (!version.empty()) {
            clGetPlatformInfo(platformArray[i], CL_PLATFORM_NAME, strSize, version.data(), nullptr);
        }
        LOGD("found platform version: %s", version.c_str());
    }
    
    cl_uint num_devices;
    clError = clGetDeviceIDs(platformArray[0], CL_DEVICE_TYPE_DEFAULT, 1, &deviceId, &num_devices);
    if (clError != CL_SUCCESS) {
        LOGE("clGetDeviceIDs(%i) failed", clError);
        return false;
    }
    LOGD("clGetDeviceIDs success, device_id:%p", (void *) deviceId); // 修正格式说明符
    
    cl_context_properties contextProperties[] = {CL_CONTEXT_PLATFORM, (cl_context_properties) platformArray[0], 0};
    clContext = clCreateContextFromType(contextProperties, CL_DEVICE_TYPE_GPU, nullptr, nullptr, &clError);
    if (clError != CL_SUCCESS) {
        LOGE("creating cl_context with GPU failed: %d", clError);
        clContext = clCreateContextFromType(contextProperties, CL_DEVICE_TYPE_CPU, nullptr, nullptr, &clError);
        if (clError != CL_SUCCESS) {
            LOGE("creating cl_context with CPU failed: %d", clError);
            return false;
        }
    }
    
    auto temp = (size_t) nullptr;
    clError = clGetContextInfo(clContext, CL_CONTEXT_DEVICES, sizeof(cl_device_id), &deviceId, &temp);
    if (clError != CL_SUCCESS) {
        LOGE("clGetContextInfo failed: %d", clError);
        return false;
    }
    
    clCommandQueue = clCreateCommandQueue(clContext, deviceId, 0, &clError);
    if (clError != CL_SUCCESS) {
        LOGE("clCreateCommandQueue failed: %d", clError);
        clError = clReleaseContext(clContext);
        if (clError != CL_SUCCESS) {
            LOGE("clReleaseContext failed: %d", clError);
        }
    }
    
    for (auto &kernelInfo: kernelInfoMap) {
        auto clProgramSrc = kernelInfo.second.programSource;
        auto clProgramSrcLen = strlen(clProgramSrc);
        auto clProgram = clCreateProgramWithSource(clContext, 1, (const char **) &clProgramSrc, &clProgramSrcLen,
                                                   &clError);
        if (clError != CL_SUCCESS) {
            LOGE("clCreateProgramWithSource %s failed: %d", kernelInfo.second.kernelName, clError);
            return false;
        }
        kernelInfo.second.clProgram = clProgram;
        auto buildOptions = getBuildOptions();
        clError = clBuildProgram(clProgram, 1, &deviceId, buildOptions.c_str(), nullptr, nullptr);
        if (clError != CL_SUCCESS) {
            size_t strSize = 0;
            clGetProgramBuildInfo(clProgram, deviceId, CL_PROGRAM_BUILD_LOG, 0, nullptr, &strSize);
            std::string log(strSize, 0);
            if (!log.empty()) {
                clGetProgramBuildInfo(clProgram, deviceId, CL_PROGRAM_BUILD_LOG, strSize, log.data(), nullptr);
            }
            LOGE("clBuildProgram failed: %d, %s", clError, log.c_str());
            return false;
        }
        
        cl_kernel kernel;
        clError = createKernelHelper(clProgram, kernelInfo.second.kernelName, kernel);
        if (clError != CL_SUCCESS) {
            LOGE("clCreateKernel failed: %d", clError);
            return false;
        }
        kernelInfo.second.clKernel = kernel;
    }
    
    return true;
}

ClProcessor::ClProcessor() : kernelInfoMap() {
    if (!initOpenCL()) {
        deinit();
    }
}

void ClProcessor::setBufferSize(size_t size) {
    bufferSize = size;
}

void ClProcessor::run(FilterTag filterTag, uint8_t *rgba, int width, int height, uint8_t *renderedRgba) {
    
    if (!clContext || !clCommandQueue || !kernelInfoMap[filterTag].clKernel) {
        LOGE("OpenCL components not initialized properly");
        return;
    }
    auto clKernel = kernelInfoMap[filterTag].clKernel;
    
    cl_int clError = CL_SUCCESS;
    // 创建缓冲区
    if (!clInputBuffer) {
        clInputBuffer = clCreateBuffer(clContext, CL_MEM_READ_ONLY, bufferSize, nullptr, &clError);
        if (clError != CL_SUCCESS) {
            LOGE("clCreateBuffer input failed: %d", clError);
            return;
        }
    }
    if (!clOutputBuffer) {
        clOutputBuffer = clCreateBuffer(clContext, CL_MEM_WRITE_ONLY, bufferSize, nullptr, &clError);
        if (clError != CL_SUCCESS) {
            LOGE("clCreateBuffer output failed: %d", clError);
            clReleaseMemObject(clInputBuffer);
            return;
        }
    }
    
    clError = clEnqueueWriteBuffer(clCommandQueue, clInputBuffer, CL_TRUE, 0, bufferSize, rgba, 0, nullptr, nullptr);
    if (clError != CL_SUCCESS) {
        LOGE("clEnqueueWriteBuffer failed: %d", clError);
        return;
    }
    
    clSetKernelArg(clKernel, 0, sizeof(cl_mem), &clInputBuffer);
    clSetKernelArg(clKernel, 1, sizeof(cl_uint), &width);
    clSetKernelArg(clKernel, 2, sizeof(cl_uint), &height);
    clSetKernelArg(clKernel, 3, sizeof(cl_mem), &clOutputBuffer);
    
    size_t globalWorkSize[2] = {static_cast<size_t>(width), static_cast<size_t>(height)};
    clEnqueueNDRangeKernel(clCommandQueue, clKernel, 2, nullptr, globalWorkSize, nullptr, 0, nullptr, nullptr);
    
    // 等待命令队列完成
    clFinish(clCommandQueue);
    
    clError = clEnqueueReadBuffer(clCommandQueue, clOutputBuffer, CL_TRUE, 0, bufferSize, renderedRgba, 0, nullptr,
                                  nullptr);
    if (clError != CL_SUCCESS) {
        LOGE("clEnqueueReadBuffer failed: %d", clError);
    }
}


void ClProcessor::run(FilterTag filterTag, uint8_t *rgba, int width, int height, uint8_t *lutTable, int lutTableSize,
                      uint8_t *renderedRgba) {
    
    if (!clContext || !clCommandQueue || !kernelInfoMap[filterTag].clKernel) {
        LOGE("OpenCL components not initialized properly");
        return;
    }
    auto clKernel = kernelInfoMap[filterTag].clKernel;
    
    cl_int clError = CL_SUCCESS;
    // 创建缓冲区
    if (!clInputBuffer) {
        clInputBuffer = clCreateBuffer(clContext, CL_MEM_READ_ONLY, bufferSize, nullptr, &clError);
        if (clError != CL_SUCCESS) {
            LOGE("clCreateBuffer input failed: %d", clError);
            return;
        }
    }
    if (!clLutTableInputBuffer) {
        clLutTableInputBuffer = clCreateBuffer(clContext, CL_MEM_READ_ONLY, lutTableSize, nullptr, &clError);
        if (clError != CL_SUCCESS) {
            LOGE("clCreateBuffer lutTable input failed: %d", clError);
            return;
        }
    }
    if (!clOutputBuffer) {
        clOutputBuffer = clCreateBuffer(clContext, CL_MEM_WRITE_ONLY, bufferSize, nullptr, &clError);
        if (clError != CL_SUCCESS) {
            LOGE("clCreateBuffer output failed: %d", clError);
            clReleaseMemObject(clInputBuffer);
            return;
        }
    }
    
    clError = clEnqueueWriteBuffer(clCommandQueue, clInputBuffer, CL_TRUE, 0, bufferSize, rgba, 0, nullptr, nullptr);
    if (clError != CL_SUCCESS) {
        LOGE("clEnqueueWriteBuffer input failed: %d", clError);
        return;
    }
    
    clError = clEnqueueWriteBuffer(clCommandQueue, clLutTableInputBuffer, CL_TRUE, 0, lutTableSize, lutTable, 0,
                                   nullptr, nullptr);
    if (clError != CL_SUCCESS) {
        LOGE("clEnqueueWriteBuffer lutTable input failed: %d", clError);
        return;
    }
    
    clSetKernelArg(clKernel, 0, sizeof(cl_mem), &clInputBuffer);
    clSetKernelArg(clKernel, 1, sizeof(cl_uint), &width);
    clSetKernelArg(clKernel, 2, sizeof(cl_uint), &height);
    clSetKernelArg(clKernel, 3, sizeof(cl_mem), &clLutTableInputBuffer);
    clSetKernelArg(clKernel, 4, sizeof(cl_mem), &clOutputBuffer);
    
    size_t globalWorkSize[2] = {static_cast<size_t>(width), static_cast<size_t>(height)};
    clEnqueueNDRangeKernel(clCommandQueue, clKernel, 2, nullptr, globalWorkSize, nullptr, 0, nullptr, nullptr);
    
    // 等待命令队列完成
    clFinish(clCommandQueue);
    
    clError = clEnqueueReadBuffer(clCommandQueue, clOutputBuffer, CL_TRUE, 0, bufferSize, renderedRgba, 0, nullptr,
                                  nullptr);
    if (clError != CL_SUCCESS) {
        LOGE("clEnqueueReadBuffer failed: %d", clError);
    }
}


void ClProcessor::deinit() {
    if (clInputBuffer) {
        clReleaseMemObject(clInputBuffer);
    }
    if (clLutTableInputBuffer) {
        clReleaseMemObject(clLutTableInputBuffer);
    }
    if (clOutputBuffer) {
        clReleaseMemObject(clOutputBuffer);
    }
    for (auto &kernelInfo: kernelInfoMap) {
        if (kernelInfo.second.clKernel) {
            clReleaseKernel(kernelInfo.second.clKernel);
            kernelInfo.second.clKernel = nullptr;
        }
        if (kernelInfo.second.clProgram) {
            clReleaseProgram(kernelInfo.second.clProgram);
            kernelInfo.second.clProgram = nullptr;
        }
    }
    if (clCommandQueue) {
        clReleaseCommandQueue(clCommandQueue);
        clCommandQueue = nullptr;
    }
    if (clContext) {
        clReleaseContext(clContext);
        clContext = nullptr;
    }
}

ClProcessor::~ClProcessor() {
    deinit();
}
