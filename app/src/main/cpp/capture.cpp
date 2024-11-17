//
// Created by muqing on 2024/10/16.
// Email: qfq61@qq.com
//

#include "capture.h"

#define TAG "NativeCaptureManager"

CaptureManager::~CaptureManager() {
    std::unique_lock<std::mutex> lock(mutex);
    while (isRunning) {
        quitCond.wait(lock);
    }
    LOGD("%s, CaptureManager released", __FUNCTION__);
}

CaptureManager::CaptureManager() {
    LOGD("%s, CaptureManager created", __FUNCTION__);
}

void CaptureManager::addCapture(int jobId, CaptureType captureType, int frameSize, std::string timeStamp, std::vector<float> exposureTimes) {
    LOGD("%s, addCapture job:%d, jobs size:%u", __FUNCTION__, jobId, jobs.size());
    auto job = std::make_shared<CaptureJob>(jobId, captureType, timeStamp, frameSize);
    job->exposureTimes = exposureTimes;
    auto it = jobs.find(jobId);
    jobs[jobId] = job;
}

void CaptureManager::collectFrame(int jobId, cv::Mat frame) {
    LOGD("%s, job-%d, jobs.size:%u", __FUNCTION__, jobId, jobs.size());
    if (jobs.size() == 0) {
        LOGE("%s, no jobs", __FUNCTION__ );
        return;
    }
    auto it = jobs.find(jobId);
    if (it != jobs.end()) {
        jobs[jobId]->frames.emplace_back(frame);
        LOGD("%s, job-%d, frameSize:%d, already has:%d", __FUNCTION__ , jobId, jobs[jobId]->frameSize,
             jobs[jobId]->frames.size());
        if (jobs[jobId]->frames.size() == jobs[jobId]->frameSize) {
            // 不能传入 &jobId，因为jobId是栈内申请的int变量，退栈后会被清空
            post(kMessage_Process, &(it->second->id));
        }
    }
}

void CaptureManager::updateCaptureBackupFilePath(int jobId, const std::string &backupFilePath) {
    auto it = jobs.find(jobId);
    if (it != jobs.end()) {
        jobs[jobId]->backupFilePath = backupFilePath;
    }
}

// reference: https://docs.opencv.org/4.x/d3/db7/tutorial_hdr_imaging.html
void CaptureManager::process(int jobId) {
    LOGD("%s, begin job-%d", __FUNCTION__ , jobId);
    auto it = jobs.find(jobId);
    if (it != jobs.end()) {
        auto start_t = cv::getTickCount();
        // // hdr 图片不能用普通的jpeg格式保存， 故在这里只生成 fusion 照片
        // need transport expsosure times
        // Mat response;
        // cv::Ptr<cv::CalibrateDebevec> calibrate = cv::createCalibrateDebevec();
        // calibrate->process(images, response, exposure_times);
        // Ptr<MergeDebevec> merge_debevec = cv::createMergeDebevec();
        // merge_debevec->process(images, hdr, exposure_times, response);
        //
        // cv::Ptr<cv::Tonemap> tonemap = cv::createTonemap(2.2f);
        // tonemap->process(hdr, ldr);
        
        cv::Mat fusion;
        cv::Ptr<cv::MergeMertens> merge_mertens = cv::createMergeMertens();
        // todo: whether need to convert to RGB
        // cv::Mat rgbMat;
        // cv::cvtColor(yuvMat, rgbMat, cv::COLOR_YUV420sp2RGB);
        merge_mertens->process(jobs[jobId]->frames, fusion);
        auto hdr_t= cv::getTickCount();
        // int64 必须要转成 int，否则输出会丢失精度后变成负值
        LOGD("%s, hdr processing cost %d s", __FUNCTION__, static_cast<int>((hdr_t - start_t) /
        cv::getTickFrequency()));

        // // default set jpeg quality to 95
        // std::vector<int> params{cv::IMWRITE_JPEG_QUALITY, 95};
        // std::vector<uchar> buffer;
        // cv::imencode(".jpg", fusion, buffer, params);
        
        if (jobs[jobId]->backupFilePath.size() > 0) {
            cv::imwrite(jobs[jobId]->backupFilePath, fusion);
        } else {
            LOGE("%s, job %d has no backup file path", __FUNCTION__, jobId);
        }
    }
    LOGD("%s, end job-%d", __FUNCTION__ , jobId);
}

void CaptureManager::handle(int what, void *data) {
    switch (what) {
        case kMessage_Process: {
            int jobId = *reinterpret_cast<int *>(data);
            process(jobId);
            break;
        }
    }
}
