//
// Created by fuqin on 2025/8/8.
//

#include "cpu-processor.h"
#include "filter.h"


void CpuProcessor::process(FilterTag filterTag, uint8_t *rgba, int width, int height, uint8_t *renderedRgba) {
    switch (filterTag) {
        case FilterTag::GREY:
            for (size_t j = 0; j < width * height * 4; j += 4) {
                uint8_t wm = rgba[j] * 0.3 + rgba[j + 1] * 0.59 + rgba[j + 2] * 0.11;
                renderedRgba[j] = wm;
                renderedRgba[j + 1] = wm;
                renderedRgba[j + 2] = wm;
                renderedRgba[j + 3] = rgba[j + 3];
            }
            break;
        case FilterTag::BLACK_WHITE: {
            uint8_t threshold = 0.5 * 255;
            uint8_t mean;
            for (size_t j = 0; j < width * height * 4; j += 4) {
                mean = (rgba[j] + rgba[j + 1] + rgba[j + 2]) / 3.0;
                if (mean > threshold) {
                    renderedRgba[j] = 255;
                    renderedRgba[j + 1] = 255;
                    renderedRgba[j + 2] = 255;
                    renderedRgba[j + 3] = rgba[j + 3];
                } else {
                    renderedRgba[j] = 0;
                    renderedRgba[j + 1] = 0;
                    renderedRgba[j + 2] = 0;
                    renderedRgba[j + 3] = rgba[j + 3];
                }
            }
            break;
        }
        case FilterTag::REVERSE:
            for (size_t j = 0; j < width * height * 4; j += 4) {
                renderedRgba[j] = 255 - rgba[j];
                renderedRgba[j + 1] = 255 - rgba[j + 1];
                renderedRgba[j + 2] = 255 - rgba[j + 2];
                renderedRgba[j + 3] = rgba[j + 3];
            }
            break;
        case FilterTag::BRIGHTNESS: {
            float *hsl = new float[width * height * 4];
            for (size_t j = 0; j < width * height * 4; j += 4) {
                Color::rgba2hsl(rgba + j, hsl + j);
                hsl[j + 2] += 0.15;
                Color::hsl2rgba(hsl + j, renderedRgba + j);
            }
            delete[] hsl;
            break;
        }
        case FilterTag::POSTERIZATION: {
            float *hsl = new float[width * height * 4];
            for (size_t j = 0; j < width * height * 4; j += 4) {
                float grey = rgba[j] * 0.3 + rgba[j + 1] * 0.59 + rgba[j + 2] * 0.11;
                Color::rgba2hsl(rgba + j, hsl + j);
                if (grey < 0.3 * 255) {
                    if (hsl[j] < 0.68 || hsl[j] > 0.66) {
                        hsl[j] = 0.67;
                    }
                    hsl[j + 1] += 0.3;
                } else if (grey > 0.7 * 255) {
                    if (hsl[j] < 0.18 || hsl[j] > 0.16) {
                        hsl[j] = 0.17;
                    }
                    hsl[j + 1] -= 0.3;
                }
                Color::hsl2rgba(hsl + j, reinterpret_cast<uint8_t *>(renderedRgba + j));
            }
            delete[] hsl;
            break;
        }
        
    }
}


void
CpuProcessor::process(FilterTag filterTag, uint8_t *rgba, int width, int height, uint8_t *lutTable, int lutTableSize,
                      uint8_t *renderedRgba) {
    for (size_t j = 0; j < width * height * 4; j += 4) {
        auto r = rgba[j];
        auto g = rgba[j + 1];
        auto b = rgba[j + 2];
        int b_div_4 = b / 4;
        int g_div_4 = g / 4;
        int r_div_4 = r / 4;
        int b_div_4_mod_8 = b_div_4 % 8;
        size_t lutIndex = ((b_div_4 / 8 * 64 + g_div_4) * 512 + (b_div_4_mod_8 * 64 + r_div_4)) * 4;
        auto lutPixel = lutTable + lutIndex;
        renderedRgba[j] = lutPixel[0]; // r
        renderedRgba[j + 1] = lutPixel[1]; //g
        renderedRgba[j + 2] = lutPixel[2]; //b
        renderedRgba[j + 3] = rgba[j]; //a
    }
}
