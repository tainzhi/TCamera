#include <jni.h>
#include <android/log.h>
#include "opencv2/opencv.hpp"
#include "opencv2/core/ocl.hpp"
#include "opencv2/core.hpp"
#include "image-processor.h"
#include "util.h"
#include "engine.h"

#define TAG "NativeImageProcessorJNI"

Engine *engine = nullptr;

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

extern "C" JNIEXPORT void JNICALL
Java_com_tainzhi_android_tcamera_ImageProcessor_init(JNIEnv *env, jobject thiz, jstring cache_path) {
    LOGV("init");
    // https://github.com/opencv/opencv/wiki/OpenCL-optimizations
    cv::ocl::Context ctx = cv::ocl::Context::getDefault();
    if (!ctx.ptr())
    {
        LOGV("opencv:opencl is not available");
    } else {
        LOGV("opencv:opencl is available");
    }
    // cv::setUseOptimized(true); enable SIMD optimized
    LOGV("cv use optimized: %d", cv::useOptimized());
    engine = new Engine();
    engine->init();
}
extern "C"
JNIEXPORT void JNICALL
Java_com_tainzhi_android_tcamera_ImageProcessor_deinit(JNIEnv *env, jobject thiz) {
    delete engine;
    LOGV("deinit");
}

/**
 * @exposure_time in nanoseconds
 */
extern "C" JNIEXPORT void JNICALL
Java_com_tainzhi_android_tcamera_ImageProcessor_processImage(JNIEnv *env, jobject thiz, jint job_id, jobject y_plane,
                                                             jobject u_plane, jobject v_plane, jint width,
                                                             jint height) {
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
    
    engine->processImage(job_id, rgbMat);
    
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
        fusion = fusion * 255;
        auto fusion_jpeg = ImageProcessor::convertMatToJpeg(fusion);
        std::string dump_fusion_jpeg_path = jstring_to_string(env, cache_path)+ '/' +
                                         std::to_string(Util::getCurrentTimestampMs()) + ".fusion_.jpeg";
        LOGD("%s dump hdr jpeg to %s", __FUNCTION__, dump_fusion_jpeg_path.c_str());
        Util::dumpBinary(dump_fusion_jpeg_path.c_str(),reinterpret_cast<uchar *>(fusion_jpeg.data()), fusion_jpeg.size());
        
        imageMats.clear();
        imageExposureTimes.clear();
#endif
    
}

extern "C" JNIEXPORT void JNICALL
Java_com_tainzhi_android_tcamera_ImageProcessor_capture(JNIEnv *env, jobject thiz, jint capture_type, jint job_id,
                                                        jstring time_stamp, jint frame_size, jobject exposure_times) {
    LOGD("%s", __FUNCTION__);
    // java 传过来的exposure_time 是纳秒，需要转换为秒
    // 获取List类和相关方法ID
    jclass listClass = env->FindClass("java/util/List");
    jmethodID sizeMethod = env->GetMethodID(listClass, "size", "()I");
    jmethodID getMethod = env->GetMethodID(listClass, "get", "(I)Ljava/lang/Object;");
    std::vector<float> exposureTimes;
    for (int i = 0; i < frame_size; i++) {
        jobject item = env->CallObjectMethod(exposure_times, getMethod, i);
        jlong exposureTime = env->CallLongMethod(item, env->GetMethodID(env->GetObjectClass(item), "longValue", "()J"));
        exposureTimes.push_back(exposureTime / 1000000000.0);
    }
    engine->addCapture(job_id, static_cast<CaptureType>(capture_type), jstring_to_string(env, time_stamp),
                       frame_size, exposureTimes);
}

extern "C" JNIEXPORT void JNICALL
Java_com_tainzhi_android_tcamera_ImageProcessor_updateCaptureBackupFilePath(JNIEnv *env, jobject thiz, jstring path) {
}
extern "C" JNIEXPORT void JNICALL
Java_com_tainzhi_android_tcamera_ImageProcessor_handlePreviewImage(JNIEnv *env, jobject thiz, jobject image) {
    // 获取 Image 类的类对象
    jclass imageClass = env->GetObjectClass(image);
    jmethodID getPlanesMethod = env->GetMethodID(imageClass, "getPlanes", "()[Landroid/media/Image$Plane;");

    // 调用 getPlanes 方法获取 Plane 数组
    jobjectArray planes = (jobjectArray) env->CallObjectMethod(image, getPlanesMethod);

    // 获取 Plane 数组的长度
    int planeCount = env->GetArrayLength(planes);

    for (int i = 0; i < planeCount; i++) {
        // 获取每个 Plane 对象
        jobject plane = env->GetObjectArrayElement(planes, i);

        // 获取 Plane 类的类对象
        jclass planeClass = env->GetObjectClass(plane);
        jmethodID getBufferMethod = env->GetMethodID(planeClass, "getBuffer", "()Ljava/nio/ByteBuffer;");
        jmethodID getRowStrideMethod = env->GetMethodID(planeClass, "getRowStride", "()I");
        jmethodID getPixelStrideMethod = env->GetMethodID(planeClass, "getPixelStride", "()I");

        // 调用 getBuffer 方法获取 ByteBuffer
        jobject buffer = env->CallObjectMethod(plane, getBufferMethod);

        // 调用 getRowStride 和 getPixelStride 方法获取行间距和像素间距
        jint rowStride = env->CallIntMethod(plane, getRowStrideMethod);
        jint pixelStride = env->CallIntMethod(plane, getPixelStrideMethod);

        // // 获取 ByteBuffer 的直接缓冲区
        // jbyteArray byteArray = (jbyteArray) env->NewByteArray(rowStride);
        // env->GetByteArrayRegion((jbyteArray) buffer, 0, rowStride, byteArray);
        //
        // // 处理图像数据
        // jsize length = env->GetArrayLength(byteArray);
        // jbyte *bytes = env->GetByteArrayElements(byteArray, nullptr);
        //
        // // 这里可以对 bytes 进行处理
        // __android_log_print(ANDROID_LOG_DEBUG, "JNI", "Processing plane %d with %d bytes", i, length);
        //
        // env->ReleaseByteArrayElements(byteArray, bytes, 0);
        // env->DeleteLocalRef(byteArray);
    }
}
