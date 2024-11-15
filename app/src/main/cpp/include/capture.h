//
// Created by muqing on 2024/10/16.
// Email: qfq61@qq.com
//

#ifndef TCAMERA_CAPTURE_H
#define TCAMERA_CAPTURE_H

#include <vector>
#include <opencv2/core/mat.hpp>
#include <opencv2/photo.hpp>
#include <opencv2/imgcodecs.hpp>
#include "looper.h"
#include "util.h"

enum  CaptureType {
    UNKNOWN,
    JPEG,
    HDR,
    VIDEO
};

struct CaptureJob {
    int id;
    CaptureType captureType;
    std::string timeStamp;
    int frameSize;
    int frameWidth;
    int frameHeight;
    std::string backupFilePath;
    std::vector<cv::Mat> frames;
    std::vector<float> exposureTimes;
    CaptureJob(int id, CaptureType captureType, std::string timeStamp, int frameSize): id(id),
    captureType(captureType), timeStamp(timeStamp), frameSize(frameSize) {}
};

class CaptureManager: Looper {
public:
    ~CaptureManager();
    CaptureManager();
    void addCapture(int jobId, CaptureType captureType, int frameSize, std::string timeStamp, std::vector<float> exposureTimes);
    void updateCaptureBackupFilePath(int jobId, const std::string &backupFilePath);
    void collectFrame(int jobId, cv::Mat frame);
private:
    void handle(int what, void *data);
    void process(int jobId);
    
    std::unordered_map<int, std::shared_ptr<CaptureJob>> jobs = {};
    std::mutex mutex;
    bool isRunning = false;
    std::condition_variable quitCond;
    
    
    
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
