#include <jni.h>
#include <android/log.h>
#include "opencv2/opencv.hpp"
#include "opencv2/core/ocl.hpp"
#include "opencv2/core.hpp"
#include "image-processor.h"
#include "util.h"

#define TAG "NativeImageProcessorJNI"

// #define TEST

std::vector<cv::Mat> imageMats;
// exposureTime in second
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

/**
 * @exposure_time in nanoseconds
 */
extern "C"
JNIEXPORT void JNICALL
Java_com_tainzhi_android_tcamera_ImageProcessor_processImage(JNIEnv *env, jobject thiz, jstring cache_path,
                                                             jobject y_plane, jobject u_plane, jobject v_plane,
                                                             jint width, jint height, jlong exposure_time) {
    jbyte* yPlane = (jbyte*)env->GetDirectBufferAddress(y_plane);
    jbyte* uPlane = (jbyte*)env->GetDirectBufferAddress(u_plane);
    // jbyte* vPlane = (jbyte*)env->GetDirectBufferAddress(v_plane);
    cv::Mat yuvMat(height + height/2, width, CV_8UC1);
    memcpy(yuvMat.data, yPlane, height * width);
    // 在这里使用的是 plane[0] + plane[1]
    // camera2 YUV420_888 的 plane[1] 存储 UVUV...UVU, 最后一个V无效，丢弃了，故需要减1
    memcpy(yuvMat.data + width * height, uPlane, height * width / 2 - 1);
    // // 当然也可以使用 plane[0] = plane[2], 那么就要使用 COLOR_YUV2RGBA_NV12
    // // camera2 YUV420_888 的 plane[2] 存储 VUVU...VUV, 最后一个U无效，丢弃了，故需要减1
    // // memcpy(yuvMat.data + width * height, vPlane, height * width / 2 - 1);

#ifdef TEST
    std::string dump_yuv_path = jstring_to_string(env, cache_path)+ '/' +
                                 std::to_string(Util::getCurrentTimestampMs())  + std::to_string(imageMats
                                 .size()) + ".yuv";
    LOGD("%s dump %d x %d hdr yuv to %s", __FUNCTION__, width, height, dump_yuv_path.c_str());
    Util::dumpBinary(dump_yuv_path.c_str(),reinterpret_cast<uchar *>(yuvMat.data), height * width * 1.5);
#endif


    cv::Mat rgbMat;
    cv::cvtColor(yuvMat, rgbMat, cv::COLOR_YUV420sp2RGB);
    
    if (imageMats.size() < 3) {
        imageMats.emplace_back(rgbMat);
        // java 传过来的exposure_time 是纳秒，需要转换为秒
        imageExposureTimes.emplace_back(exposure_time / 1000000000.0);
    }
    if(imageMats.size() == 3) {
        LOGD("complete 3 images, to process hdr, with exposure times %f, %f, %f", imageExposureTimes[0],
             imageExposureTimes[1], imageExposureTimes[2]);
        // cv 处理生成后的 hdr 不能用普通的图像格式比如jpeg存储，比如用 Radiance Image(.hdr)格式村此时
        // 处理后返回的hdr 的值在 [0,1]之间，所以需要乘以 255
        Mat hdr = cv::Mat();
        Mat ldr = cv::Mat();
        Mat fusion = cv::Mat();
        ImageProcessor::process(imageMats, imageExposureTimes, hdr, ldr, fusion);

#ifdef TEST
        // cv 生成的 hdr 不能直接保存为 jpeg, 只能保存为 hdr 格式。
        hdr = hdr * 255;
        auto hdr_jpeg = ImageProcessor::convertMatToJpeg(hdr);
        std::string dump_hdr_jpeg_path = jstring_to_string(env, cache_path)+ '/' +
                std::to_string(Util::getCurrentTimestampMs()) + ".hdr_.jpeg";
        LOGD("%s dump hdr jpeg to %s", __FUNCTION__, dump_hdr_jpeg_path.c_str());
        Util::dumpBinary(dump_hdr_jpeg_path.c_str(),reinterpret_cast<uchar *>(hdr_jpeg.data()), hdr_jpeg.size());
        
        // 要把 hdr 映射成 ldr，才能保存为 jpeg 格式
        ldr = ldr * 255;
        auto ldr_jpeg = ImageProcessor::convertMatToJpeg(ldr);
        std::string dump_ldr_jpeg_path = jstring_to_string(env, cache_path)+ '/' +
                                         std::to_string(Util::getCurrentTimestampMs()) + ".ldr_.jpeg";
        LOGD("%s dump hdr jpeg to %s", __FUNCTION__, dump_ldr_jpeg_path.c_str());
        Util::dumpBinary(dump_ldr_jpeg_path.c_str(),reinterpret_cast<uchar *>(ldr_jpeg.data()), ldr_jpeg.size());
#endif
        
        
        fusion = fusion * 255;
        auto fusion_jpeg = ImageProcessor::convertMatToJpeg(fusion);
        std::string dump_fusion_jpeg_path = jstring_to_string(env, cache_path)+ '/' +
                                         std::to_string(Util::getCurrentTimestampMs()) + ".fusion_.jpeg";
        LOGD("%s dump hdr jpeg to %s", __FUNCTION__, dump_fusion_jpeg_path.c_str());
        Util::dumpBinary(dump_fusion_jpeg_path.c_str(),reinterpret_cast<uchar *>(fusion_jpeg.data()), fusion_jpeg.size());
        
        imageMats.clear();
        imageExposureTimes.clear();
    }
}