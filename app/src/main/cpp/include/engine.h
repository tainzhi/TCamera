//
// Created by muqing on 2024/10/14.
// Email: qfq61@qq.com
//

#ifndef TCAMERA_ENGINE_H
#define TCAMERA_ENGINE_H

#include "util.h"
#include "capture.h"

#define TAG "NativeEngine"

class Engine {
public:
    Engine();
    void init();
    void processImage(int jobId, cv::Mat &image);
    void addCapture(int jobId, CaptureType captureType, std::string timeStamp, int frameSize, std::vector<float>
            exposureTimes);
    void deinit();
    ~Engine();
    static Engine *getInstance();
    static void resetInstance();
private:
    CaptureManager *capture;
};


#endif //TCAMERA_ENGINE_H
