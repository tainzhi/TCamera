//
// Created by fuqin on 2025/8/8.
//

#ifndef TCAMERA_PROCESSOR_H
#define TCAMERA_PROCESSOR_H

#include <stdint.h>

enum class FilterTag;

class BaseProcessor {
public:
    virtual ~BaseProcessor() = default;
    
    virtual void setBufferSize(size_t size) = 0;
    
    virtual void process(FilterTag filterTag, uint8_t *rgba, int width, int height, uint8_t *renderedRgba) = 0;
    
    virtual void process(FilterTag filterTag, uint8_t *rgba, int width, int height, uint8_t *lutTable, int lutTableSize,
                         uint8_t *renderedRgba) = 0;
};

#endif //TCAMERA_PROCESSOR_H
