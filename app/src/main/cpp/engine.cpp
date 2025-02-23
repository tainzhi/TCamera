//
// Created by muqing on 2024/10/14.
// Email: qfq61@qq.com
//

#include "engine.h"
#include "capture.h"
#include "filter.h"

#define TAG "NativeEngine"


void Engine::init(){
}

std::shared_ptr<CaptureManager> Engine::getCaptureManager() {
    return getThread(captureManagerHolder, this);
}

std::shared_ptr<FilterManager> Engine::getFilterManager() {
    return getThread(filterHolder, this);
}

void Engine::deinit() {
    getFilterManager()->quit();
}

Engine::~Engine(){
}