//
// Created by muqing on 2024/10/14.
// Email: qfq61@qq.com
//

#include "engine.h"

#define TAG "NativeEngine"

Engine::Engine(std::string cachePath): cachePath(cachePath) {}

void Engine::init(){
}

std::shared_ptr<CaptureManager> Engine::getCaptureManager() {
    return getThread<CaptureManager>(captureManagerHolder);
}

void Engine::processImage(int jobId, cv::Mat &image) {
    getCaptureManager()->collectFrame(jobId, image);
}

void Engine::addCapture(int jobId, CaptureType captureType, std::string timeStamp, int frameSize, std::vector<float> exposureTimes) {
    getCaptureManager()->addCapture(jobId, captureType, frameSize, std::move(timeStamp), std::move(exposureTimes));
}


void Engine::deinit() {
}

Engine::~Engine(){
}