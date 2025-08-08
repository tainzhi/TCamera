//
// Created by fuqin on 2025/8/8.
//

#ifndef TCAMERA_CPU_PROCESSOR_H
#define TCAMERA_CPU_PROCESSOR_H

#include "processor.h"

enum class FilterTag;

class CpuProcessor : public BaseProcessor {
public:
    void process(FilterTag filterTag, uint8_t *rgba, int width, int height, uint8_t *renderedRgba) override;
    
    void process(FilterTag filterTag, uint8_t *rgba, int width, int height, uint8_t *lutTable, int lutTableSize,
                 uint8_t *renderedRgba) override;
    
    void setBufferSize(size_t size) {};
    
};


#endif //TCAMERA_CPU_PROCESSOR_H
