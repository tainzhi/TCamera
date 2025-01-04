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

void Engine::collectImage(int jobId, cv::Mat &image) {
    getCaptureManager()->collectFrame(jobId, image);
}

void Engine::addCapture(int jobId, CaptureType captureType, std::string timeStamp, int orientation, int frameSize,
                        std::vector<float> exposureTimes) {
    getCaptureManager()->addCapture(jobId, captureType, std::move(timeStamp), orientation, frameSize, std::move
    (exposureTimes));
}


void Engine::deinit() {
    getFilterManager()->quit();
}

Engine::~Engine(){
}