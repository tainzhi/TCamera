//
// Created by muqing on 2024/10/14.
// Email: qfq61@qq.com
//

#include "engine.h"

Engine::Engine(std::string cachePath): cachePath(cachePath) {}

void Engine::init(){
    capture = new CaptureManager(cachePath);
}

void Engine::processImage(int jobId, cv::Mat &image) {
    capture->collectFrame(jobId, image);
}

void Engine::addCapture(int jobId, CaptureType captureType, std::string timeStamp, int frameSize, std::vector<float> exposureTimes) {
    capture->addCapture(jobId, captureType, frameSize, std::move(timeStamp), std::move(exposureTimes));
}


void Engine::deinit() {
    delete capture;
}

Engine::~Engine(){
}