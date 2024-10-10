//
// Created by muqing on 2024/10/10.
// Email: qfq61@qq.com
//

#ifndef TCAMERA_IMAGE_PROCESSOR_H
#define TCAMERA_IMAGE_PROCESSOR_H

#endif //TCAMERA_IMAGE_PROCESSOR_H

#include <vector>
#include <opencv2/core/mat.hpp>
#include <opencv2/photo.hpp>
#include <opencv2/imgcodecs.hpp>
#include <jni.h>
#include "util.h"

using namespace cv;

class ImageProcessor {
public:
  static Mat process(std::vector<cv::Mat> &images, std::vector<float> &exposure_times);
  static std::vector<uchar> convertMatToJpeg(cv::Mat &mat, int quality=100);
};
