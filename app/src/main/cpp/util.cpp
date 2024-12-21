//
// Created by muqing on 2024/10/10.
// Email: qfq61@qq.com
//

#include "util.h"

#define TAG "NativeUtil"


JavaVM * Util::gCachedJavaVm = nullptr;
unsigned long long Util::getCurrentTimestampMs()
{
    struct timeval tv;
    gettimeofday(&tv, nullptr);
    unsigned long long timestampMs;
    timestampMs = ((unsigned long long)tv.tv_sec * 1000) + (tv.tv_usec / 1000000);
    return timestampMs;
}

std::string Util::jstring_to_string(JNIEnv *env, jstring jstr) {
    const char *cstring = env->GetStringUTFChars(jstr, nullptr);
    if (cstring == nullptr) {
        // 异常处理
        return "";
    }
    
    std::string result(cstring);
    env->ReleaseStringUTFChars(jstr, cstring);
    return result;
}

void Util::jobject_to_stringVector(JNIEnv *env, jobject jList, std::vector<std::string> &result) {
    jclass listClass = env->GetObjectClass(jList);
    jmethodID sizeMethod = env->GetMethodID(listClass, "size", "()I");
    jmethodID getMethod = env->GetMethodID(listClass, "get", "(I)Ljava/lang/Object;");

    jint size = env->CallIntMethod(jList, sizeMethod);
    for (jint i = 0; i < size; ++i) {
        jobject jstrObj = env->CallObjectMethod(jList, getMethod, i);
        jstring jstr = static_cast<jstring>(jstrObj);
        const char *cstring = env->GetStringUTFChars(jstr, nullptr);
        result.emplace_back(cstring);
        env->ReleaseStringUTFChars(jstr, cstring);
        env->DeleteLocalRef(jstr);
    }
    env->DeleteLocalRef(listClass);
}

void Util::jobject_to_intVector(JNIEnv *env, jobject jList, std::vector<int> &result) {
    jclass listClass = env->GetObjectClass(jList);
    jmethodID sizeMethod = env->GetMethodID(listClass, "size", "()I");
    jmethodID getMethod = env->GetMethodID(listClass, "get", "(I)Ljava/lang/Object;");

    jint size = env->CallIntMethod(jList, sizeMethod);
    for (jint i = 0; i < size; ++i) {
        jobject jintObj = env->CallObjectMethod(jList, getMethod, i);
        jint value = env->CallIntMethod(jintObj, env->GetMethodID(env->GetObjectClass(jintObj), "intValue", "()I"));
        result.push_back(value);
        env->DeleteLocalRef(jintObj);
    }
    env->DeleteLocalRef(listClass);
}


bool Util::get_env(JNIEnv **env)
{
    // bool needsDetach = false;
    jint envResult = Util::gCachedJavaVm->GetEnv(reinterpret_cast<void **>(env), JNI_VERSION_1_6);
    switch(envResult) {
        case JNI_OK:
            break;
        case JNI_EDETACHED: {
            JavaVMAttachArgs args = {JNI_VERSION_1_6, nullptr, nullptr};
            envResult = Util::gCachedJavaVm->AttachCurrentThread(env, (void *) &args);
            if (envResult != JNI_OK) {
                LOGE("%s, failed to attache current thread to JVM", __FUNCTION__);
                return JNI_ERR;
            }
            // needsDetach = true;
        }
            break;
        default:
            LOGE("%s, attach current thread failed, unknown reason", __FUNCTION__);
            return JNI_ERR;
    }
    return JNI_OK;
}

void Util::release_env()
{
//    if (needsDetach) {
//        Util::gCachedJavaVm->DetachCurrentThread();
//    }
    Util::gCachedJavaVm->DetachCurrentThread();
}

bool Util::dumpBinary(const char * path, void *data, size_t size)
{
    bool success = false;
    int file_fd = open(path, O_RDWR | O_CREAT, 0777);
    if (file_fd >= 0)
    {
        int32_t written = write(file_fd, data, size);
        if (written < size)
        {
            LOGE("%s Error in write binary to %s", __FUNCTION__, path);
            success = false;
        }
        success = true;
    } else {
        LOGE("%s Error in opening file %s error = %d", __FUNCTION__, path, errno);
        success = false;
    }
    close(file_fd);
    return success;
}

void Util::handleEnvException(JNIEnv * env)
{
    if (env->ExceptionCheck()) {
        jthrowable exception = env->ExceptionOccurred();
        env->ExceptionClear();

        // 获取 Throwable 类
        jclass throwableClass = env->FindClass("java/lang/Throwable");
        if (throwableClass == nullptr) {
            LOGE("Failed to find Throwable class");
            return;
        }

        // 获取 printStackTrace 方法
        jmethodID printStackTraceMethod = env->GetMethodID(throwableClass, "printStackTrace", "(Ljava/io/PrintWriter;)V");
        if (printStackTraceMethod == nullptr) {
            LOGE("Failed to find printStackTrace method");
            env->DeleteLocalRef(throwableClass);
            return;
        }

        // 获取 PrintWriter 类
        jclass printWriterClass = env->FindClass("java/io/PrintWriter");
        if (printWriterClass == nullptr) {
            LOGE("Failed to find PrintWriter class");
            env->DeleteLocalRef(throwableClass);
            return;
        }

        // 获取 StringWriter 类
        jclass stringWriterClass = env->FindClass("java/io/StringWriter");
        if (stringWriterClass == nullptr) {
            LOGE("Failed to find StringWriter class");
            env->DeleteLocalRef(throwableClass);
            env->DeleteLocalRef(printWriterClass);
            return;
        }

        // 创建 StringWriter 实例
        jmethodID stringWriterConstructor = env->GetMethodID(stringWriterClass, "<init>", "()V");
        jobject stringWriter = env->NewObject(stringWriterClass, stringWriterConstructor);
        if (stringWriter == nullptr) {
            LOGE("Failed to create StringWriter instance");
            env->DeleteLocalRef(throwableClass);
            env->DeleteLocalRef(printWriterClass);
            env->DeleteLocalRef(stringWriterClass);
            return;
        }

        // 创建 PrintWriter 实例
        jmethodID printWriterConstructor = env->GetMethodID(printWriterClass, "<init>", "(Ljava/io/Writer;)V");
        jobject printWriter = env->NewObject(printWriterClass, printWriterConstructor, stringWriter);
        if (printWriter == nullptr) {
            LOGE("Failed to create PrintWriter instance");
            env->DeleteLocalRef(throwableClass);
            env->DeleteLocalRef(printWriterClass);
            env->DeleteLocalRef(stringWriterClass);
            env->DeleteLocalRef(stringWriter);
            return;
        }

        // 调用 printStackTrace 方法
        env->CallVoidMethod(exception, printStackTraceMethod, printWriter);

        // 获取 StringWriter 的 toString 方法
        jmethodID toStringMethod = env->GetMethodID(stringWriterClass, "toString", "()Ljava/lang/String;");
        jstring stackTraceString = (jstring) env->CallObjectMethod(stringWriter, toStringMethod);

        // 将 jstring 转换为 const char*
        const char* stackTraceChars = env->GetStringUTFChars(stackTraceString, nullptr);
        LOGE("Java Exception Stack Trace:\n%s", stackTraceChars);

        // 释放资源
        env->ReleaseStringUTFChars(stackTraceString, stackTraceChars);
        env->DeleteLocalRef(stackTraceString);
        env->DeleteLocalRef(printWriter);
        env->DeleteLocalRef(stringWriter);
        env->DeleteLocalRef(stringWriterClass);
        env->DeleteLocalRef(printWriterClass);
        env->DeleteLocalRef(throwableClass);
    }
}
