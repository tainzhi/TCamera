#version 300 es
#extension GL_OES_EGL_image_external_essl3: require
precision mediump float;

in vec2 v_texCoord;
in float weight[5];
int weightSize = 5;
out vec4 outColor;
uniform vec2 u_TextureSize;
uniform samplerExternalOES u_TextureSampler;
uniform int u_IsTrueAspectRatio;
uniform vec2 u_Size;
uniform vec2 u_Direction;


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
        vec4 textureColor = texture(u_TextureSampler, v_texCoord);
        for (int i = 1; i < weightSize; i++) {
            vec2 offset = float(i) * u_Direction * u_Size;
            textureColor += texture(u_TextureSampler, v_texCoord + offset) * weight[i];
            textureColor += texture(u_TextureSampler, v_texCoord - offset) * weight[i];
        }
        mixColor = mix(textureColor, vec4(0.0, 0.0, 0, smoothedAlpha), smoothedAlpha);
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
    outColor = mixColor;
}