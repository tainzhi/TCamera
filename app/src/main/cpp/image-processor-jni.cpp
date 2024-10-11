#include <jni.h>
#include <android/log.h>
#include "opencv2/opencv.hpp"
#include "opencv2/core/ocl.hpp"
#include "opencv2/core.hpp"
#include "image-processor.h"
#include "util.h"

#define TAG "NativeImageProcessorJNI"

std::vector<cv::Mat> imageMats;
std::vector<float> imageExposureTimes;

static std::string jstring_to_string(JNIEnv *env, jstring jstr) {
    const char *cstring = env->GetStringUTFChars(jstr, nullptr);
    if (cstring == nullptr) {
// 异常处理
        return "";
    }
    
    std::string result(cstring);
    env->ReleaseStringUTFChars(jstr, cstring);
    
    return result;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_tainzhi_android_tcamera_ImageProcessor_init(JNIEnv *env, jobject thiz) {
    cv::Mat mat;
    cv::UMat umat;
    LOGV("init");
    // https://github.com/opencv/opencv/wiki/OpenCL-optimizations
    cv::ocl::Context ctx = cv::ocl::Context::getDefault();
    if (!ctx.ptr())
    {
        LOGV("opencv:opencl is not available");
    } else {
        LOGV("opencv:opencl is available");
    }
}
extern "C"
JNIEXPORT void JNICALL
Java_com_tainzhi_android_tcamera_ImageProcessor_deinit(JNIEnv *env, jobject thiz) {
    LOGV("deinit");
}

extern "C"
JNIEXPORT void JNICALL
Java_com_tainzhi_android_tcamera_ImageProcessor_processImage(JNIEnv *env, jobject thiz, jstring cache_path,
                                                             jobject y_plane, jobject u_plane, jobject v_plane,
                                                             jint width, jint height, jlong exposure_time) {
    jbyte* yPlane = (jbyte*)env->GetDirectBufferAddress(y_plane);
    jbyte* uPlane = (jbyte*)env->GetDirectBufferAddress(u_plane);
    jbyte* vPlane = (jbyte*)env->GetDirectBufferAddress(v_plane);
    cv::Mat yuvMat(height + height/2, width, CV_8UC1);
    memcpy(yuvMat.data, yPlane, height * width);
    memcpy(yuvMat.data + width * height, uPlane, height * width / 4);
    memcpy(yuvMat.data + width * height + width * height / 4, vPlane, height * width / 4);
    // cv::Mat yuvMat(height + height/2, width, CV_8UC1);
    // // Get the Y plane
    // jbyte* yPlane = (jbyte*) env->GetDirectBufferAddress(y_plane);
    // for (int i = 0; i < height; i++) {
    //     for (int j = 0; j < width; j++) {
    //         yuvMat.at<uchar>(i, j) = yPlane[i * width + j];
    //     }
    // }
    //
    // int h_offset = height, w_offset = 0;
    // // Get the U plane
    // jbyte* uPlane = (jbyte*)env->GetDirectBufferAddress(u_plane);
    // for (int i = 0; i < height / 2; i++) {
    //     for (int j = 0; j < width / 2; j++) {
    //         yuvMat.at<uchar>(h_offset, w_offset) = uPlane[i * width / 2 + j];
    //         w_offset++;
    //         if (w_offset >= width) {
    //             w_offset = 0;
    //             h_offset++;
    //         }
    //     }
    // }
    //
    // // Get the V plane
    // jbyte* vPlane = (jbyte*)env->GetDirectBufferAddress(v_plane);
    // for (int i = 0; i < height / 2; i++) {
    //     for (int j = 0; j < width / 2; j++) {
    //         yuvMat.at<uchar>(h_offset, w_offset) = vPlane[i * width / 2 + j];
    //         w_offset++;
    //         if (w_offset >= width) {
    //             w_offset = 0;
    //             h_offset++;
    //         }
    //     }
    // }
    std::string dump_yuv_path = jstring_to_string(env, cache_path)+ '/' +
                                 std::to_string(Util::getCurrentTimestampMs())  + std::to_string(imageMats
                                 .size()) + ".yuv";
    LOGD("%s dump %d x %d hdr yuv to %s", __FUNCTION__, width, height, dump_yuv_path.c_str());
    Util::dumpBinary(dump_yuv_path.c_str(),reinterpret_cast<uchar *>(yuvMat.data), height * width * 1.5);


    // Convert YUV to BGR
    // https://www.jianshu.com/p/11365d423d26
    // https://gist.github.com/FWStelian/4c3dcd35960d6eabbe661c3448dd5539
    cv::Mat rgbMat;
    cv::cvtColor(yuvMat, rgbMat, cv::COLOR_YUV420p2RGB);
    
    if (imageMats.size() < 3) {
        imageMats.emplace_back(rgbMat);
        imageExposureTimes.emplace_back(exposure_time);
    }
    if(imageMats.size() == 3) {
        LOGD("complete 3 images, to process hdr");
        auto mat = ImageProcessor::process(imageMats, imageExposureTimes);
        auto jpeg = ImageProcessor::convertMatToJpeg(mat);
        std::string dump_jpeg_path = jstring_to_string(env, cache_path)+ '/' +
                std::to_string(Util::getCurrentTimestampMs()) + ".jpeg";
        LOGD("%s dump hdr jpeg to %s", __FUNCTION__, dump_jpeg_path.c_str());
        Util::dumpBinary(dump_jpeg_path.c_str(),reinterpret_cast<uchar *>(jpeg.data()), jpeg.size());
        imageMats.clear();
        imageExposureTimes.clear();
    }
}