//
// Created by muqing on 2024/10/14.
// Email: qfq61@qq.com
//

#ifndef TCAMERA_ENGINE_H
#define TCAMERA_ENGINE_H

#include "util.h"
#include "capture.h"
#include "thread.h"
#include "filter.h"
#include <jni.h>

class Engine {
public:
    void init();
    void deinit();
    ~Engine();
    std::shared_ptr<CaptureManager> getCaptureManager();
    std::shared_ptr<FilterManager> getFilterManager();
private:
    ThreadHolder<CaptureManager> captureManagerHolder;
    ThreadHolder<FilterManager> filterHolder;
};


#endif //TCAMERA_ENGINE_H
