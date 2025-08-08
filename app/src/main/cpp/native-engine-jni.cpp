#include <jni.h>
#include <android/log.h>
#include <format>
#include "opencv2/opencv.hpp"
#include "opencv2/core/ocl.hpp"
#include "opencv2/core.hpp"
#include "CL/opencl.hpp"
#include "util.h"
#include "engine.h"
#include "capture.h"
#include "filter.h"
#include "color.h"

#define TAG "NativeEngineJNI"
// #define DEBUG

#define BUILD_CLASS_GLOBAL_REF(FILEDNAME, CLASSNAME) \
    do {                                             \
        fields. FILEDNAME = env->FindClass(k ## CLASSNAME ## ClassPathName); \
        if (fields. FILEDNAME == nullptr) {          \
            LOGE("Failed to find class %s", CLASSNAME); \
            return; \
        }                                            \
        fields. FILEDNAME = static_cast<jclass>(env->NewGlobalRef(fields. FILEDNAME)); \
        if (fields. FILEDNAME == nullptr) {          \
            LOGE("Failed to create global ref for %s", CLASSNAME);          \
            return; \
        }                                            \
        LOGD("class %s retrieved (clazz=%p)", k ## CLASSNAME ## ClassPathName, fields. FILEDNAME); \
    } while(0)

#define DESTROY_CLASS_GLOBAL_REF(FIELDNAME) \
    do {                                    \
        if (fields. FIELDNAME != nullptr) { \
            env->DeleteGlobalRef(fields. FIELDNAME); \
            LOGD("destroy global ref %s", FIELDNAME); \
        }                                   \
    } while(0)

static const char *kImageProcessorClassPathName = "com/tainzhi/android/tcamera/ImageProcessor";
Engine *engine = nullptr;

fields_t fields;

extern "C" JNIEXPORT void JNICALL
Engine_init(JNIEnv *env, jobject thiz, jobject context) {
    LOGV("init");
    
    fields.image_processor = env->FindClass(kImageProcessorClassPathName);
    if (fields.image_processor == nullptr) {
        LOGE("Failed to find class %s", kImageProcessorClassPathName);
        return;
    }
    fields.image_processor = static_cast<jclass>(env->NewGlobalRef(fields.image_processor));
    if (fields.image_processor == nullptr) {
        LOGE("Failed to make global reference to %s class.", kImageProcessorClassPathName);
        return;
    } else {
        LOGD("class image_processor retrieved (clazz=%p)", fields.image_processor);
    }
    
    jclass contextClass = env->GetObjectClass(context);
    if (contextClass == NULL) {
        LOGD("Failed to get context class");
        return;
    }
    jmethodID getCacheDirMethod = env->GetMethodID(contextClass, "getCacheDir", "()Ljava/io/File;");
    if (getCacheDirMethod == NULL) {
        LOGD("Failed to get getCacheDir method ID");
        return;
    }
    jobject fileObject = env->CallObjectMethod(context, getCacheDirMethod);
    if (fileObject == NULL) {
        LOGD("Failed to call getCacheDir method");
        return;
    }
    jclass fileClass = env->GetObjectClass(fileObject);
    if (fileClass == NULL) {
        LOGD("Failed to get File class");
        return;
    }
    jmethodID getAbsolutePathMethod = env->GetMethodID(fileClass, "getAbsolutePath", "()Ljava/lang/String;");
    if (getAbsolutePathMethod == NULL) {
        LOGD("Failed to get getAbsolutePath method ID");
        return;
    }
    jstring pathString = (jstring) env->CallObjectMethod(fileObject, getAbsolutePathMethod);
    if (pathString == NULL) {
        LOGD("Failed to call getAbsolutePath method");
        return;
    } else {
        LOGD("cache dir path: %s", Util::jstring_to_string(env, pathString).c_str());
    }
    env->DeleteLocalRef(contextClass);
    env->DeleteLocalRef(fileObject);
    env->DeleteLocalRef(fileClass);
    
    // https://github.com/opencv/opencv/wiki/OpenCL-optimizations
    cv::ocl::Context ctx = cv::ocl::Context::getDefault();
    if (!ctx.ptr()) {
        LOGV("opencv:opencl is not available");
    } else {
        LOGV("opencv:opencl is available");
    }
    // cv::setUseOptimized(true); enable SIMD optimized
    LOGV("opencv use optimized: %d", cv::useOptimized());
    LOGD("opencv build info: %s", cv::getBuildInformation().c_str());
    LOGD("opencv cpu features:%s", cv::getCPUFeaturesLine().c_str());
    LOGD("opencv ipp:%d, version:%s, status:%d", cv::ipp::useIPP(), cv::ipp::getIppVersion().c_str(),
         cv::ipp::getIppStatus());
    LOGD("opencv %d threads, %d cpus to accelerate", cv::getNumThreads(), cv::getNumberOfCPUs());
    
    // check opencl
    std::vector<cl::Platform> platforms;
    cl::Platform::get(&platforms);
    if (platforms.empty()) {
        LOGE("No OpenCL platforms found.");
    } else {
        LOGD("OpenCL has %d platforms", platforms.size());
    }
    std::stringstream ss;
    // Iterate over platforms and list devices
    for (const auto &platform : platforms) {
        ss << "Platform: " << platform.getInfo<CL_PLATFORM_NAME>() << std::endl;

        // Get devices for the current platform
        std::vector<cl::Device> devices;
        platform.getDevices(CL_DEVICE_TYPE_ALL, &devices);

        if (devices.empty()) {
            ss << "  No devices found on this platform." << std::endl;
            continue;
        }

        // List devices
        for (const auto &device : devices) {
            ss << "  Device: " << device.getInfo<CL_DEVICE_NAME>() << std::endl;
            ss << "    Type: ";
            cl_device_type deviceType = device.getInfo<CL_DEVICE_TYPE>();
            if (deviceType & CL_DEVICE_TYPE_CPU) {
                ss << "CPU ";
            }
            if (deviceType & CL_DEVICE_TYPE_GPU) {
                ss << "GPU ";
            }
            if (deviceType & CL_DEVICE_TYPE_ACCELERATOR) {
                ss << "Accelerator ";
            }
            ss << std::endl;
        }
    }
    auto result = new std::string(ss.str());
    LOGD("OpenCL %s", result->c_str());
    
    engine = new Engine();
    engine->init();
    Util::cachePath = Util::jstring_to_string(env, pathString);
}

extern "C" JNIEXPORT void JNICALL
Engine_deinit(JNIEnv *env, jobject thiz) {
    LOGV("deinit");
    if (fields.image_processor != nullptr) {
        env->DeleteGlobalRef(fields.image_processor);
        LOGD("destroy global ref %s", "field.image_processor");
    }
    engine->deinit();
    delete engine;
}

/**
 * @exposure_time in nanoseconds
 */
extern "C" JNIEXPORT void JNICALL
Engine_collectImage(JNIEnv *env, jobject thiz, jint job_id, jint filter_tag, jobject y_plane, jobject u_plane,
                            jobject v_plane, jint width, jint height) {
    LOGD("begin");
    jbyte *yPlane = (jbyte *) env->GetDirectBufferAddress(y_plane);
    jbyte *uPlane = (jbyte *) env->GetDirectBufferAddress(u_plane);
    // jbyte* vPlane = (jbyte*)env->GetDirectBufferAddress(v_plane);
    cv::Mat yuvMat(height + height / 2, width, CV_8UC1);
    memcpy(yuvMat.data, yPlane, height * width);
    // 在这里使用的是 plane[0] + plane[1]
    // camera2 YUV420_888 的 plane[1] 存储 UVUV...UVU, 最后一个V无效，丢弃了，故需要减1
    memcpy(yuvMat.data + width * height, uPlane, height * width / 2 - 1);
    // // 当然也可以使用 plane[0] = plane[2], 那么就要使用 COLOR_YUV2RGBA_NV12
    // // camera2 YUV420_888 的 plane[2] 存储 VUVU...VUV, 最后一个U无效，丢弃了，故需要减1
    // // memcpy(yuvMat.data + width * height, vPlane, height * width / 2 - 1);

#ifdef DEBUG
    std::string dump_yuv_path = std::format("{}/collect_image_{}x{}_{}.420sp.yuv", Util::cachePath, width, height,
                                            Util::getCurrentTimestampMs());
    LOGD("dump %d x %d hdr yuv to %s", width, height, dump_yuv_path.c_str());
    Util::dumpBinary(dump_yuv_path.c_str(),reinterpret_cast<uchar *>(yuvMat.data), height * width * 1.5);
#endif
    
    engine->getCaptureManager()->collectFrame(job_id, filter_tag, yuvMat);
    LOGD("end");
}

extern "C" JNIEXPORT void JNICALL
Engine_capture(JNIEnv *env, jobject thiz, jint job_id, jint capture_type, jstring time_stamp, jint orientation,
                       jint frame_size, jobject exposure_times) {
    LOGD();
    // java 传过来的exposure_time 是纳秒，需要转换为秒
    // 获取List类和相关方法ID
    jclass listClass = env->FindClass("java/util/List");
    jmethodID getMethod = env->GetMethodID(listClass, "get", "(I)Ljava/lang/Object;");
    std::vector<float> exposureTimes;
    for (int i = 0; i < frame_size; i++) {
        jobject item = env->CallObjectMethod(exposure_times, getMethod, i);
        jlong exposureTime = env->CallLongMethod(item, env->GetMethodID(env->GetObjectClass(item), "longValue", "()J"));
        exposureTimes.push_back(exposureTime / 1000000000.0);
    }
    env->DeleteLocalRef(listClass);
    engine->getCaptureManager()->addCapture(job_id, static_cast<CaptureType>(capture_type),
                                            Util::jstring_to_string(env, time_stamp), orientation, frame_size,
                                            exposureTimes);
}

extern "C" JNIEXPORT void JNICALL
Engine_abortCapture(JNIEnv *env, jobject thiz, jint job_id) {
    LOGD("abort job-%d", job_id);
};

extern "C" JNIEXPORT jboolean JNICALL
Engine_configureFilterThumbnails(JNIEnv *env, jobject thiz, jint thumbnail_width, jint thumbnail_height,
                                         jobject filter_names, jobject filter_tags, jobject filter_thumbnail_bitmaps,
                                         jobject lut_bitmaps) {
    LOGD();
    engine->getFilterManager()->configureThumbnails(env, thumbnail_width, thumbnail_height, filter_names, filter_tags,
                                                    filter_thumbnail_bitmaps, lut_bitmaps);
    return true;
}

extern "C" JNIEXPORT jboolean JNICALL
Engine_processFilterThumbnails(JNIEnv *env, jobject thiz, jobject image, jint orientation,
                                       jint updateRangeStart, jint updateRangeEnd) {
    LOGD();
    // 获取 Image 类的类对象
    jclass imageClass = env->GetObjectClass(image);
    jmethodID getFormatMethod = env->GetMethodID(imageClass, "getFormat", "()I");
    jmethodID getPlanesMethod = env->GetMethodID(imageClass, "getPlanes", "()[Landroid/media/Image$Plane;");
    jint format = env->CallIntMethod(image, getFormatMethod);
    assert(format == 35); // 35 is ImageFormat.YUV_420_888
    // 调用 getPlanes 方法获取 Plane 数组
    jobjectArray planes = (jobjectArray) env->CallObjectMethod(image, getPlanesMethod);
    jint width = env->CallIntMethod(image, env->GetMethodID(imageClass, "getWidth", "()I"));
    jint height = env->CallIntMethod(image, env->GetMethodID(imageClass, "getHeight", "()I"));
    // 获取 Plane 数组的长度
    int planeCount = env->GetArrayLength(planes);
    if (planeCount != 3) {
        LOGE("planeCount:%d, not 3", planeCount);
        return false;
    }
    jclass planeClass = env->FindClass("android/media/Image$Plane");
    jmethodID getBufferMethod = env->GetMethodID(planeClass, "getBuffer", "()Ljava/nio/ByteBuffer;");
    // jmethodID getPixelStrideMethod = env->GetMethodID(planeClass, "getPixelStride", "()I");
    // jmethodID getRowStrideMethod = env->GetMethodID(planeClass, "getRowStride", "()I");
    jobject yPlane = env->GetObjectArrayElement(planes, 0);
    jobject yBuffer = env->CallObjectMethod(yPlane, getBufferMethod);
    jbyte *yBytes = (jbyte *) env->GetDirectBufferAddress(yBuffer);
    
    jobject uPlane = env->GetObjectArrayElement(planes, 1);
    jobject uBuffer = env->CallObjectMethod(uPlane, getBufferMethod);
    jbyte *uBytes = (jbyte *) env->GetDirectBufferAddress(uBuffer);
    // jint uPixelStride = env->CallIntMethod(uPlane, env->GetMethodID(planeClass, "getPixelStride", "()I"));
    // jint uRowStride = env->CallIntMethod(uPlane, env->GetMethodID(planeClass, "getRowStride", "()I"));
    //
    // jobject vPlane = env->GetObjectArrayElement(planes, 2);
    // jobject vBuffer = env->CallObjectMethod(vPlane, getBufferMethod);
    // jint vPixelStride = env->CallIntMethod(vPlane, env->GetMethodID(planeClass, "getPixelStride", "()I"));
    // jint vRowStride = env->CallIntMethod(vPlane, env->GetMethodID(planeClass, "getRowStride", "()I"));
    
    // 必须要在堆上申请内存，否则在传递到另一个线程时会被释放导致内存错误
    // todo: use SharedPtr or 对于过量的process thumbnail请求处理，进行适当的丢弃
    auto yuvBuffer = std::make_shared<Color::YuvBuffer>(width, height);
    memcpy(yuvBuffer->data, yBytes, height * width);
    // camera2 YUV420_888 的 plane[1] 存储 UVUV...UVU, 最后一个V无效，丢弃了，故需要减1
    memcpy(yuvBuffer->data + width * height, uBytes, height * width / 2 - 1);
    
    engine->getFilterManager()->sendProcessThumbnails(yuvBuffer, orientation, updateRangeStart, updateRangeEnd);
    env->DeleteLocalRef(yBuffer);
    env->DeleteLocalRef(uBuffer);
    env->DeleteLocalRef(planeClass);
    env->DeleteLocalRef(yPlane);
    env->DeleteLocalRef(uPlane);
    env->DeleteLocalRef(planes);
    env->DeleteLocalRef(imageClass);
    return true;
}


extern "C" JNIEXPORT jboolean JNICALL
Engine_applyFilterEffectToJpeg(JNIEnv *env, jobject thiz, jint jobId, jint filterTag, jobject jpegImage) {
    LOGD();
    // 获取 Image 类的类对象
    jclass imageClass = env->GetObjectClass(jpegImage);
    jmethodID getFormatMethod = env->GetMethodID(imageClass, "getFormat", "()I");
    jmethodID getPlanesMethod = env->GetMethodID(imageClass, "getPlanes", "()[Landroid/media/Image$Plane;");
    jint format = env->CallIntMethod(jpegImage, getFormatMethod);
    assert(format == 256); // 256 is ImageFormat.JPEG
    // 调用 getPlanes 方法获取 Plane 数组
    jobjectArray planes = (jobjectArray) env->CallObjectMethod(jpegImage, getPlanesMethod);
    // 获取 Plane 数组的长度
    int planeCount = env->GetArrayLength(planes);
    if (planeCount != 1) {
        LOGE("planeCount:%d, not 1", planeCount);
        return false;
    }
    jclass planeClass = env->FindClass("android/media/Image$Plane");
    jmethodID getBufferMethod = env->GetMethodID(planeClass, "getBuffer", "()Ljava/nio/ByteBuffer;");
    jobject plane = env->GetObjectArrayElement(planes, 0);
    jobject buffer = env->CallObjectMethod(plane, getBufferMethod);
    jbyte *imageBytes = (jbyte *) env->GetDirectBufferAddress(buffer);
    jint byteSize = env->GetDirectBufferCapacity(buffer);
    uint8_t *bytes = new uint8_t[byteSize];
    memcpy(bytes, imageBytes, byteSize);
    engine->getFilterManager()->sendApplyFilterEffectToJpeg(jobId, filterTag, bytes, byteSize);
    env->DeleteLocalRef(buffer);
    env->DeleteLocalRef(planeClass);
    env->DeleteLocalRef(plane);
    env->DeleteLocalRef(planes);
    env->DeleteLocalRef(imageClass);
    return true;
}

extern "C" JNIEXPORT void JNICALL
Engine_clearFilterThumbnails(JNIEnv *env, jobject thiz, jint selectedFilterTag) {
    LOGD();
    engine->getFilterManager()->sendClearThumbnails(selectedFilterTag);
}


static JNINativeMethod methods[] = {{"init",                      "(Landroid/content/Context;)V",                                          (void *) Engine_init},
                                    {"deinit",                    "()V",                                                                   (void *) Engine_deinit},
                                    {"collectImage",              "(IILjava/nio/ByteBuffer;Ljava/nio/ByteBuffer;"
                                                                  "Ljava/nio/ByteBuffer;II)V",                                             (void *) Engine_collectImage},
                                    {"capture",                   "(IILjava/lang/String;IILjava/util/List;)V",                             (void *) Engine_capture},
                                    {"abortCapture",              "(I)V",                                                                  (void *) Engine_abortCapture},
                                    {"configureFilterThumbnails", "(IILjava/util/List;Ljava/util/List;Ljava/util/List;Ljava/util/List;)Z", (void *) Engine_configureFilterThumbnails},
                                    {"processFilterThumbnails",   "(Landroid/media/Image;III)Z",                                           (void *) Engine_processFilterThumbnails},
                                    {"applyFilterEffectToJpeg",   "(IILandroid/media/Image;)Z",                                            (void *) Engine_applyFilterEffectToJpeg},
                                    {"clearFilterThumbnails",     "(I)V",                                                                  (void *) Engine_clearFilterThumbnails},};


extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    LOGD("JNI_onLoad");
    JNIEnv *env = nullptr;
    if (vm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        LOGE("failed to get env");
        return JNI_ERR;
    }
    jclass clazz;
    clazz = env->FindClass(kImageProcessorClassPathName);
    if (clazz == nullptr) {
        LOGE("failed to find %s", kImageProcessorClassPathName);
        return JNI_ERR;
    }
    bool registerResult = env->RegisterNatives(clazz, methods, sizeof(methods) / sizeof(methods[0]));
    if (registerResult != JNI_OK) {
        LOGE("failed to register natives methods");
        return JNI_ERR;
    }
    Util::gCachedJavaVm = vm;
    return JNI_VERSION_1_6;
}