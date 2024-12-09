#include <jni.h>
#include <android/log.h>
#include "opencv2/opencv.hpp"
#include "opencv2/core/ocl.hpp"
#include "opencv2/core.hpp"
#include "util.h"
#include "engine.h"

#define TAG "NativeImageProcessorJNI"

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

static const char * kImageProcessorClassPathName = "com/tainzhi/android/tcamera/ImageProcessor";
Engine *engine = nullptr;

fields_t fields;

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
ImageProcessor_init(JNIEnv *env, jobject thiz, jobject context) {
    LOGV("init");

    fields.image_processor = env->FindClass(kImageProcessorClassPathName);
    if (fields.image_processor == nullptr) {
        LOGE("%s, Failed to find class %s", __FUNCTION__, kImageProcessorClassPathName);
        return;
    }
    fields.image_processor = static_cast<jclass>(env->NewGlobalRef(fields.image_processor));
    if (fields.image_processor == nullptr) {
        LOGE("%s, Failed to make global reference to %s class.", __FUNCTION__ , kImageProcessorClassPathName);
        return;
    } else {
        LOGD("%s, class image_processor retrieved (clazz=%p)", __FUNCTION__ , fields.image_processor);
    }

    jclass contextClass = env->GetObjectClass(context);
    if (contextClass == NULL) {
        LOGD("Failed to get context class");
        return;
    }
    jmethodID getCacheDirMethod = env->GetMethodID(contextClass, "getCacheDir", "()Ljava/io/File;");
    if (getCacheDirMethod == NULL) {
        LOGD("Failed to get getCacheDir method ID");
        return ;
    }
    jobject fileObject = env->CallObjectMethod(context, getCacheDirMethod);
    if (fileObject == NULL) {
        LOGD("Failed to call getCacheDir method");
        return ;
    }
    jclass fileClass = env->GetObjectClass(fileObject);
    if (fileClass == NULL) {
        LOGD("Failed to get File class");
        return ;
    }
    jmethodID getAbsolutePathMethod = env->GetMethodID(fileClass, "getAbsolutePath", "()Ljava/lang/String;");
    if (getAbsolutePathMethod == NULL) {
        LOGD("Failed to get getAbsolutePath method ID");
        return ;
    }
    jstring pathString = (jstring) env->CallObjectMethod(fileObject, getAbsolutePathMethod);
    if (pathString == NULL) {
        LOGD("Failed to call getAbsolutePath method");
        return ;
    } else {
        LOGD("cache dir path: %s", jstring_to_string(env, pathString).c_str());
    }
    env->DeleteLocalRef(contextClass);
    env->DeleteLocalRef(fileObject);
    env->DeleteLocalRef(fileClass);

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
    
    engine = new Engine(jstring_to_string(env, pathString));
    engine->init();
}

extern "C"
JNIEXPORT void JNICALL
ImageProcessor_deinit(JNIEnv *env, jobject thiz) {
    LOGV("deinit");
    if (fields.image_processor != nullptr) {
        env->DeleteGlobalRef(fields.image_processor);
        LOGD("destroy global ref %s", "field.image_processor");
    }
    delete engine;
}

/**
 * @exposure_time in nanoseconds
 */
extern "C" JNIEXPORT void JNICALL
ImageProcessor_collectImage(JNIEnv *env, jobject thiz, jint job_id, jobject y_plane,
                                                             jobject u_plane, jobject v_plane, jint width,
                                                             jint height) {
    LOGD("%s begin", __FUNCTION__ );
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
    
    engine->collectImage(job_id, yuvMat);
    LOGD("%s end", __FUNCTION__ );
}

extern "C" JNIEXPORT void JNICALL
ImageProcessor_capture(JNIEnv *env, jobject thiz, jint job_id, jint capture_type,
                                                        jstring time_stamp, jint orientation, jint frame_size, jobject
                                                        exposure_times) {
    LOGD("%s", __FUNCTION__);
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
    engine->addCapture(job_id, static_cast<CaptureType>(capture_type), jstring_to_string(env, time_stamp), orientation,
                       frame_size, exposureTimes);
}

extern "C" JNIEXPORT void JNICALL
ImageProcessor_handlePreviewImage(JNIEnv *env, jobject thiz, jobject image) {
    LOGD("%s", __FUNCTION__);
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
        
        // kepp 保留, 不要删除
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
        env->DeleteLocalRef(buffer);
        env->DeleteLocalRef(planeClass);
    }
    jmethodID closeMethod = env->GetMethodID(imageClass, "close", "()V");
    env->CallVoidMethod(image, closeMethod);
    env->DeleteLocalRef(imageClass);
}

extern "C" JNIEXPORT void JNICALL
ImageProcessor_abortCapture(JNIEnv *env, jobject thiz, jint job_id) {
    LOGD("%s abort job-%d", __FUNCTION__, job_id);
};

static JNINativeMethod methods[] = {
    {"init", "(Landroid/content/Context;)V", (void *) ImageProcessor_init},
    {"deinit", "()V", (void *) ImageProcessor_deinit},
    {"collectImage", "(ILjava/nio/ByteBuffer;Ljava/nio/ByteBuffer;Ljava/nio/ByteBuffer;II)V", (void *) ImageProcessor_collectImage},
    {"capture", "(IILjava/lang/String;IILjava/util/List;)V", (void *) ImageProcessor_capture},
    {"handlePreviewImage", "(Landroid/media/Image;)V", (void *) ImageProcessor_handlePreviewImage},
    {"abortCapture", "(I)V", (void *) ImageProcessor_abortCapture}
};



extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    LOGD("JNI_onLoad");
    JNIEnv  *env = nullptr;
    if (vm->GetEnv((void **)&env, JNI_VERSION_1_6) != JNI_OK) {
        LOGE("%s, failed to get env", __FUNCTION__);
        return JNI_ERR;
    }
    jclass clazz;
    clazz = env->FindClass(kImageProcessorClassPathName);
    if (clazz == nullptr) {
        LOGE("%s, failed to find %s", __FUNCTION__ , kImageProcessorClassPathName);
        return JNI_ERR;
    }
    bool registerResult = env->RegisterNatives(clazz, methods, sizeof(methods)/sizeof(methods[0]));
    if (registerResult != JNI_OK) {
        LOGE("%s, failed to register natives methods", __FUNCTION__);
        return JNI_ERR;
    }
    Util::gCachedJavaVm = vm;
    return JNI_VERSION_1_6;
}

