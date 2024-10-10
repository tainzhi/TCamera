//
// Created by qiufq1 on 2023/12/15.
//

#include "image-processor.h"
// reference: https://docs.opencv.org/4.x/d3/db7/tutorial_hdr_imaging.html
// https://docs.opencv.org/3.4/d2/df0/tutorial_py_hdr.html
// https://learnopencv.com/high-dynamic-range-hdr-imaging-using-opencv-cpp-python/
Mat ImageProcessor::process(std::vector<cv::Mat> &images, std::vector<float> &exposure_times) {
    Mat response;
    cv::Ptr<cv::CalibrateDebevec> calibrate = cv::createCalibrateDebevec();
    calibrate->process(images, response, exposure_times);
    Mat hdr;
    Ptr<MergeDebevec> merge_debevec = cv::createMergeDebevec();
    merge_debevec->process(images, hdr, exposure_times, response);
    return hdr;
}

std::vector<uchar> ImageProcessor::convertMatToJpeg(cv::Mat &mat, int quality)
{
    std::vector<int> params{cv::IMWRITE_JPEG_QUALITY, quality};
    std::vector<uchar> buffer;
    cv::imencode(".jpg", mat, buffer, params);
    return buffer;
}
