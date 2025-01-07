//
// Created by muqing on 2024/10/16.
// Email: qfq61@qq.com
//

#include "capture.h"
#include "engine.h"
#include "filter.h"

#define TAG "NativeCaptureManager"

CaptureManager::CaptureManager(Engine *engine) : engine(engine){}

CaptureManager::~CaptureManager() {
    std::unique_lock<std::mutex> lock(mutex);
    while (isRunning()) {
        quitCond.wait(lock);
    }
    LOGD("CaptureManager released");
}


void
CaptureManager::addCapture(int jobId, CaptureType captureType, std::string timeStamp, int orientation, int frameSize,
                           std::vector<float> exposureTimes) {
    LOGD("addCapture job:%d, jobs size:%zu", jobId, jobs.size());
    auto job = std::make_shared<CaptureJob>(jobId, captureType, timeStamp, orientation, frameSize);
    job->exposureTimes = exposureTimes;
    jobs[jobId] = job;
}

void CaptureManager::collectFrame(int jobId, int filterTag, cv::Mat frame) {
    LOGD("job-%d, jobs.size:%zu", jobId, jobs.size());
    if (jobs.size() == 0) {
        LOGE("no jobs");
        return;
    }
    auto it = jobs.find(jobId);
    if (it != jobs.end()) {
        jobs[jobId]->frames.emplace_back(frame);
        LOGD("job-%d, frameSize:%d, already has:%zu", jobId, jobs[jobId]->frameSize,
             jobs[jobId]->frames.size());
        if (jobs[jobId]->frames.size() == jobs[jobId]->frameSize) {
            auto pCaptureMsg = new CaptureMsg(jobId, filterTag);
            post(kMessage_Process, pCaptureMsg);
        }
    }
}

// reference: https://docs.opencv.org/4.x/d3/db7/tutorial_hdr_imaging.html
void CaptureManager::recvProcess(void *data) {
    auto pCaptureMsg = reinterpret_cast<CaptureMsg *>(data);
    auto jobId = pCaptureMsg->jobId;
    LOGD("begin job-%d", jobId);
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
        // opencv hdr -> fusion 32bit浮点数 CV_32F
        merge_mertens->process(rgbMats, fusion);
        rgbMats.clear();
        // 必须把[0,1]转到[0,255], 才能保存成jpeg
        cv::Mat fusionU;
        fusion.convertTo(fusionU, CV_8U, 255.0);
        
        cv::Mat rotatedImage;
        LOGD("orientation:%d", jobs[jobId]->orientation);
        switch (jobs[jobId]->orientation) {
            case 90:
                cv::rotate(fusionU, rotatedImage, cv::ROTATE_90_CLOCKWISE);
                break;
            case 180:
                cv::rotate(fusionU, rotatedImage, cv::ROTATE_180);
                break;
            case 270:
                cv::rotate(fusionU, rotatedImage, cv::ROTATE_90_COUNTERCLOCKWISE);
                break;
        }
        fusionU.release();
        
        if (pCaptureMsg->filterTag == 0) {
            auto hdr_t= cv::getTickCount();
            // int64 必须要转成 int，否则输出会丢失精度后变成负值
            LOGD("hdr processing cost %d s", static_cast<int>((hdr_t - start_t) /
                                                              cv::getTickFrequency()));
            // not needed: 在这里无需手动转成jpeg，直接 cv:imwrite(jpeg)即可
            // // default set jpeg quality to 95
            // std::vector<int> params{cv::IMWRITE_JPEG_QUALITY, 95};
            // std::vector<uchar> buffer;
            // cv::imencode(".jpg", fusion, buffer, params);
            std::string filePath = std::format("{}/hdr_jpeg_{}.jpg", Util::cachePath, Util::getCurrentTimestampMs());
            LOGD("save hdr image to %s", filePath.c_str());
            // 把生成的写到jpeg图片写到 filePath， quality 为 100
            cv::imwrite(filePath, rotatedImage, std::vector<int>{cv::IMWRITE_JPEG_QUALITY, 100});
            Listener::onProcessed(jobId, Listener_type::Listener_type_HDR_CAPTURED, filePath);
            LOGD("end job-%d", jobId);
        } else {
            LOGD("rotated mat depth:%d, channels:%d", rotatedImage.depth(), rotatedImage.channels());
            cv::Mat yuvMat;
            // todo: use libjpeg to convert jpeg to yuv420sp, 替换掉opencv的cvtColor
            cv::cvtColor(rotatedImage, yuvMat, cv::COLOR_RGB2YUV_I420);
            // after cvtColor, yuvMat is I420, yuvMat.cols == width, yuvMat.rows == height * 3 / 2
            // I420 yyyy...uu..vv.. y,u,v分别存储，存储为所有的y, 所有的u，所有的v
            // YV12 yyyy...vv..uu.. y,u,v分别存储
            auto width = rotatedImage.cols;
            auto height = rotatedImage.rows;
            auto pYuvBuffer = new Color::YuvBuffer(width, height);
            // copy y
            memcpy(pYuvBuffer->data, yuvMat.data, pYuvBuffer->width * pYuvBuffer->height);
            int uIndex = width * height;
            for (int i = width * height; i < width * height * 5 / 4; i++) {
                pYuvBuffer->data[uIndex] = yuvMat.data[i];
                uIndex += 2;
            }
            int vIndex = width * height + 1;
            for (int i = width * height * 5 / 4; i < width * height * 3 / 2; i++) {
                pYuvBuffer->data[vIndex] = yuvMat.data[i];
                vIndex += 2;
            }
#define TEST
#ifdef TEST
            std::string yuvFilePath = std::format("{}/rotated_yuv_{}x{}_{}.420sp.yuv", Util::cachePath, width, height,
                                          Util::getCurrentTimestampMs());
            LOGD("dump jpeg yuv to %s", yuvFilePath.c_str());
            Util::dumpBinary(yuvFilePath.c_str(), pYuvBuffer->data, pYuvBuffer->width * pYuvBuffer->height * 3 / 2);
#endif
            engine->getFilterManager()->sendApplyFilterEffectToHdr(jobId, pCaptureMsg->filterTag, pYuvBuffer);
        }
    }
    // 处理完 job，从 jobs 中移除
    jobs.erase(it);
}

void CaptureManager::handle(int what, void *data) {
    switch (what) {
        case kMessage_Process: {
            recvProcess(data);
            break;
        }
    }
}
