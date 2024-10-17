//
// Created by tainzhi(@qfq61@qq.com) on 2023/12/15.
//

#include "image-processor.h"
#define TAG "NativeImageProcessor"

// reference: https://docs.opencv.org/4.x/d3/db7/tutorial_hdr_imaging.html
void ImageProcessor::process(std::vector<cv::Mat> &images, std::vector<float> &exposure_times, cv::Mat &hdr, cv::Mat &ldr,
                        cv::Mat &fusion) {
    auto start_t = cv::getTickCount();
    // // hdr 图片不能用普通的jpeg格式保存， 故在这里只生成 fusion 照片
    // Mat response;
    // cv::Ptr<cv::CalibrateDebevec> calibrate = cv::createCalibrateDebevec();
    // calibrate->process(images, response, exposure_times);
    // Ptr<MergeDebevec> merge_debevec = cv::createMergeDebevec();
    // merge_debevec->process(images, hdr, exposure_times, response);
    //
    // cv::Ptr<cv::Tonemap> tonemap = cv::createTonemap(2.2f);
    // tonemap->process(hdr, ldr);
    
    cv::Ptr<cv::MergeMertens> merge_mertens = cv::createMergeMertens();
    merge_mertens->process(images, fusion);
    LOGD("%s, process cost %d s", __FUNCTION__, (cv::getTickCount() - start_t) / cv::getTickFrequency());
}

std::vector<uchar> ImageProcessor::convertMatToJpeg(cv::Mat &mat, int quality)
{
    std::vector<int> params{cv::IMWRITE_JPEG_QUALITY, quality};
    std::vector<uchar> buffer;
    cv::imencode(".jpg", mat, buffer, params);
    return buffer;
}
