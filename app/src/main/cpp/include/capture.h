//
// Created by muqing on 2024/10/16.
// Email: qfq61@qq.com
//

#ifndef TCAMERA_CAPTURE_H
#define TCAMERA_CAPTURE_H

#include <vector>
#include <format>
#include <opencv2/core/mat.hpp>
#include <opencv2/photo.hpp>
#include <opencv2/imgcodecs.hpp>
#include "looper.h"
#include "util.h"
#include "listener.h"

enum CaptureType {
    UNKNOWN,
    JPEG,
    HDR,
    VIDEO
};

struct CaptureJob {
    int id;
    CaptureType captureType;
    std::string timeStamp;
    int orientation;
    int frameSize;
    int frameWidth;
    int frameHeight;
    std::vector<cv::Mat> frames;
    std::vector<float> exposureTimes;
    CaptureJob(int id, CaptureType captureType, std::string timeStamp, int orientation, int frameSize): id(id),
    captureType(captureType), timeStamp(timeStamp), orientation(orientation), frameSize(frameSize) {}
};

class CaptureManager: public Looper {
public:
    ~CaptureManager();
    void addCapture(int jobId, CaptureType captureType, std::string timeStamp, int orientation, int frameSize,
                    std::vector<float> exposureTimes);
    void collectFrame(int jobId, cv::Mat frame);
private:
    void handle(int what, void *data);
    void recvProcess(void *data);
    
    std::unordered_map<int, std::shared_ptr<CaptureJob>> jobs = {};
    
    
    enum kMessage {
        kMessage_Capture = 1,
        kMessage_CollectFrame = 2,
        kMessage_Process = 3,
        kMessage_UpdateCaptureBackupFilePath = 4,
        kMessage_PostComplete = 5,
        kMessage_PostError = 6
    };

};


#endif //TCAMERA_CAPTURE_H
