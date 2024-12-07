//
// Created by muqing on 2024/10/16.
// Email: qfq61@qq.com
//

#include "capture.h"

#define TAG "NativeCaptureManager"

CaptureManager::~CaptureManager() {
    std::unique_lock<std::mutex> lock(mutex);
    while (isRunning()) {
        quitCond.wait(lock);
    }
    LOGD("%s, CaptureManager released", __FUNCTION__);
}

CaptureManager::CaptureManager(std::string cachePath): cachePath(cachePath) {
    LOGD("%s, CaptureManager created", __FUNCTION__);
}

void
CaptureManager::addCapture(int jobId, CaptureType captureType, std::string timeStamp, int orientation, int frameSize,
                           std::vector<float> exposureTimes) {
    LOGD("%s, addCapture job:%d, jobs size:%u", __FUNCTION__, jobId, jobs.size());
    auto job = std::make_shared<CaptureJob>(jobId, captureType, timeStamp, orientation, frameSize);
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

// reference: https://docs.opencv.org/4.x/d3/db7/tutorial_hdr_imaging.html
void CaptureManager::process(int jobId) {
    LOGD("%s, begin job-%d", __FUNCTION__ , jobId);
    auto it = jobs.find(jobId);
    if (it != jobs.end()) {
        auto start_t = cv::getTickCount();
        // // hdr 图片不能用普通的jpeg格式保存， 故在这里只生成 fusion 照片
        // // need transport expsosure times
        // cv::Mat response;
        // auto hdr = cv::Mat();
        // auto ldr = cv::Mat();
        // cv::Ptr<cv::CalibrateDebevec> calibrate = cv::createCalibrateDebevec();
        // calibrate->process(jobs[jobId]->frames, response, jobs[jobId]->exposureTimes);
        // cv::Ptr<cv::MergeDebevec> merge_debevec = cv::createMergeDebevec();
        // merge_debevec->process(jobs[jobId]->frames, hdr, jobs[jobId]->exposureTimes), response);
        //
        // cv::Ptr<cv::Tonemap> tonemap = cv::createTonemap(2.2f);
        // tonemap->process(hdr, ldr);
        // // 注意：若要保存成 jpeg，必须把数据转成 [0,255]. 因为cv hdr算法处理后的值范围在[0,1]
        // // hdr = hdr * 255
        // // ldr = ldr * 255
        
        cv::Mat fusion;
        cv::Ptr<cv::MergeMertens> merge_mertens = cv::createMergeMertens();
        // must needed:  to convert to RGB
        std::vector<cv::Mat> rgbMats(jobs[jobId]->frameSize);
        for (size_t i = 0; i < jobs[jobId]->frameSize; i++) {
            cv::cvtColor(jobs[jobId]->frames[i], rgbMats[i], cv::COLOR_YUV420sp2RGB);
        }
        jobs[jobId]->frames.clear();
        merge_mertens->process(rgbMats, fusion);
        rgbMats.clear();
        // 必须把[0,1]转到[0,255], 才能保存成jpeg
        fusion = fusion * 255;
        
        cv::Mat rotatedImage;
        LOGD("%s, orientation:%d", __FUNCTION__, jobs[jobId]->orientation);
        switch (jobs[jobId]->orientation) {
            case 90:
                cv::rotate(fusion, rotatedImage, cv::ROTATE_90_CLOCKWISE);
                break;
            case 180:
                cv::rotate(fusion, rotatedImage, cv::ROTATE_180);
                break;
            case 270:
                cv::rotate(fusion, rotatedImage, cv::ROTATE_90_COUNTERCLOCKWISE);
                break;
        }
        fusion.release();
        
        auto hdr_t= cv::getTickCount();
        // int64 必须要转成 int，否则输出会丢失精度后变成负值
        LOGD("%s, hdr processing cost %d s", __FUNCTION__, static_cast<int>((hdr_t - start_t) /
        cv::getTickFrequency()));

        // not needed: 在这里无需手动转成jpeg，直接 cv:imwrite(jpeg)即可
        // // default set jpeg quality to 95
        // std::vector<int> params{cv::IMWRITE_JPEG_QUALITY, 95};
        // std::vector<uchar> buffer;
        // cv::imencode(".jpg", fusion, buffer, params);
        std::string filePath = cachePath + '/' +  std::to_string(Util::getCurrentTimestampMs()) + ".jpg";
        LOGD("%s, save hdr image to %s", __FUNCTION__, filePath.c_str());
        // 把生成的写到jpeg图片写到 filePath， quality 为 100
        cv::imwrite(filePath, rotatedImage, std::vector<int>{cv::IMWRITE_JPEG_QUALITY, 100});
        Listener::onCaptured(jobId, filePath);
        LOGD("%s, end job-%d", __FUNCTION__ , jobId);
    }
    // 处理完 job，从 jobs 中移除
    jobs.erase(it);
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
