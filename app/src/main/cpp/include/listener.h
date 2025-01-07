//
// Created by tainzhi on 2024/11/20.
// Email: qfq61@qq.com
//

#ifndef TCAMERA_LISTENER_H
#define TCAMERA_LISTENER_H

#include "util.h"
#include "jni.h"

enum Listener_type {
    Listener_type_HDR_CAPTURED = 0,
    Listener_type_FILTER_EFFECT_APPLIED_TO_JPEG = 1,
    Listener_type_FILTER_EFFECT_APPLIED_TO_HDR = 2,
};


class Listener {
public:
    static void onProcessed(int jobId, Listener_type type, std::string cacheImagePath);
};


#endif //TCAMERA_LISTENER_H
