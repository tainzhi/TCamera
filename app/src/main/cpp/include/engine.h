//
// Created by muqing on 2024/10/14.
// Email: qfq61@qq.com
//

#ifndef TCAMERA_ENGINE_H
#define TCAMERA_ENGINE_H

#include "util.h"
#include "capture.h"
#include <jni.h>

class Engine {
public:
    Engine(std::string cachePath);
    void init();
    void processImage(int jobId, cv::Mat &image);
    void addCapture(int jobId, CaptureType captureType, std::string timeStamp, int frameSize, std::vector<float>
            exposureTimes);
    void deinit();
    ~Engine();
    static Engine *getInstance();
    static void resetInstance();
    std::shared_ptr<CaptureManager> getCaptureManager();
private:
    std::string cachePath;
    ThreadHolder<CaptureManager> captureManagerHolder;
};


#endif //TCAMERA_ENGINE_H
