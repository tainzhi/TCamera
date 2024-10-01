#version 300 es
#extension GL_OES_EGL_image_external_essl3: require
precision mediump float;

in vec2 v_texCoord;
out vec4 outColor;
uniform vec2 u_TextureSize;
uniform samplerExternalOES u_TextureSampler;
uniform sampler2D u_textureLUT;
uniform int u_IsTrueAspectRatio;
uniform int u_filterType;//滤镜类型

//rgb转hsl
vec3 rgb2hsl(vec3 color) {
    vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
    vec4 p = mix(vec4(color.bg, K.wz), vec4(color.gb, K.xy), step(color.b, color.g));
    vec4 q = mix(vec4(p.xyw, color.r), vec4(color.r, p.yzx), step(p.x, color.r));

    float d = q.x - min(q.w, q.y);
    float e = 1.0e-10;
    return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
}

//hsl转rgb
vec3 hsl2rgb(vec3 color) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(color.xxx + K.xyz) * 6.0 - K.www);
    return color.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), color.y);
}

//灰度
void grey(inout vec4 color) {
    float weightMean = color.r * 0.3 + color.g * 0.59 + color.b * 0.11;
    color.r = color.g = color.b = weightMean;
}

//黑白
void blackAndWhite(inout vec4 color) {
    float threshold = 0.5;
    float mean = (color.r + color.g + color.b) / 3.0;
    color.r = color.g = color.b = mean >= threshold ? 1.0 : 0.0;
}

//反向
void reverse(inout vec4 color) {
    color.r = 1.0 - color.r;
    color.g = 1.0 - color.g;
    color.b = 1.0 - color.b;
}

//亮度
void light(inout vec4 color) {
    vec3 hslColor = vec3(rgb2hsl(color.rgb));
    hslColor.z += 0.15;
    color = vec4(hsl2rgb(hslColor), color.a);
}

void light2(inout vec4 color) {
    color.r += 0.15;
    color.g += 0.15;
    color.b += 0.15;
}

//查找表滤镜
vec4 lookupTable(vec4 color) {
    float blueColor = color.b * 63.0;

    vec2 quad1;
    quad1.y = floor(floor(blueColor) / 8.0);
    quad1.x = floor(blueColor) - (quad1.y * 8.0);
    vec2 quad2;
    quad2.y = floor(ceil(blueColor) / 8.0);
    quad2.x = ceil(blueColor) - (quad2.y * 8.0);

    vec2 texPos1;
    texPos1.x = (quad1.x * 0.125) + 0.5 / 512.0 + ((0.125 - 1.0 / 512.0) * color.r);
    texPos1.y = (quad1.y * 0.125) + 0.5 / 512.0 + ((0.125 - 1.0 / 512.0) * color.g);
    vec2 texPos2;
    texPos2.x = (quad2.x * 0.125) + 0.5 / 512.0 + ((0.125 - 1.0 / 512.0) * color.r);
    texPos2.y = (quad2.y * 0.125) + 0.5 / 512.0 + ((0.125 - 1.0 / 512.0) * color.g);
    vec4 newColor1 = texture(u_textureLUT, texPos1);
    vec4 newColor2 = texture(u_textureLUT, texPos2);
    vec4 newColor = mix(newColor1, newColor2, fract(blueColor));
    return vec4(newColor.rgb, color.w);
}

//色调分离
void posterization(inout vec4 color) {
    //计算灰度值
    float grayValue = color.r * 0.3 + color.g * 0.59 + color.b * 0.11;
    //转换到hsl颜色空间
    vec3 hslColor = vec3(rgb2hsl(color.rgb));
    //根据灰度值区分阴影和高光，分别处理
    if (grayValue < 0.3) {
        //添加蓝色
        if (hslColor.x < 0.68 || hslColor.x > 0.66) {
            hslColor.x = 0.67;
        }
        //增加饱和度
        hslColor.y += 0.3;
    } else if (grayValue > 0.7) {
        //添加黄色
        if (hslColor.x < 0.18 || hslColor.x > 0.16) {
            hslColor.x = 0.17;
        }
        //降低饱和度
        hslColor.y -= 0.3;
    }
    color = vec4(hsl2rgb(hslColor), color.a);
}

// from https://iquilezles.org/articles/distfunctions
// reference: https://www.shadertoy.com/view/WtdSDs
float roundedBoxSDF(vec2 CenterPosition, vec2 Size, float Radius) {
    return length(max(abs(CenterPosition) - Size + Radius, 0.0)) - Radius;
}

void main() {
    vec4 mixColor;
    if (u_IsTrueAspectRatio == 1)
    {
        // make round corner
        vec2 size = vec2(u_TextureSize.x, u_TextureSize.y);
        // bottom left as the original postion
        vec2 location = vec2(0.0, 0.0);
        float radius = 0.05 * u_TextureSize.x;
        float distance = roundedBoxSDF(v_texCoord.xy * size - location - size / 2.0, size / 2.0, radius);
        float smoothedAlpha = smoothstep(0.0, 1.0, distance);
        mixColor = mix(texture(u_TextureSampler, v_texCoord), vec4(0.0, 0.0, 0, smoothedAlpha), smoothedAlpha);
    }
    else {
        // todo: make round corner
        // 对某些机型需要输出1:1的texture，但是camera engine没有该比例的输出，只能通过shader截取，
        // 1:1 采样长方形image的正中心的正方形，其边长为长方形的短边
        float shortLength = min(u_TextureSize.x, u_TextureSize.y);
        // 计算采样区域左下角的坐标
        vec2 offset = (u_TextureSize - vec2(shortLength)) / 2.0;
        // 计算归一化纹理坐标
        vec2 normalizedTexCoord = (v_texCoord * vec2(shortLength) + offset) / u_TextureSize;
        mixColor = texture(u_TextureSampler, normalizedTexCoord);
    }
    if (u_filterType == 0) {
        outColor = mixColor;
        return;
    }
    else if (u_filterType == 1) {
        //灰度
        grey(mixColor);
    } else if (u_filterType == 2) {
        //黑白
        blackAndWhite(mixColor);
    } else if (u_filterType == 3) {
        //反向
        reverse(mixColor);
    } else if (u_filterType == 4) {
        //亮度
        light(mixColor);
    } else if (u_filterType == 5) {
        //亮度2
        light2(mixColor);
    } else if (u_filterType == 6) {
        //lut
        outColor = lookupTable(mixColor);
        return;
    } else if (u_filterType == 7) {
        //色调分离
        posterization(mixColor);
    }
    outColor = mixColor;
}