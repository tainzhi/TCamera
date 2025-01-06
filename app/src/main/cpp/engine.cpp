//
// Created by muqing on 2024/10/14.
// Email: qfq61@qq.com
//

#include "engine.h"

#define TAG "NativeEngine"


void Engine::init(){
}

std::shared_ptr<CaptureManager> Engine::getCaptureManager() {
    return getThread(captureManagerHolder);
}

std::shared_ptr<FilterManager> Engine::getFilterManager() {
    return getThread(filterHolder);
}

void Engine::deinit() {
    getFilterManager()->quit();
}

Engine::~Engine(){
}