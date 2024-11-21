//
// Created by tainzhi on 2024/11/20.
// Email: qfq61@qq.com
//

#ifndef TCAMERA_LISTENER_H
#define TCAMERA_LISTENER_H

#include "util.h"
#include "jni.h"

class Listener {
public:
    static void onCaptured(int jobId, std::string cacheImagePath);
};


#endif //TCAMERA_LISTENER_H
