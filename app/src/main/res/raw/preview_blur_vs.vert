#version 300 es

layout(location = 0) in vec4 a_Position;
layout(location = 1) in vec2 a_TexturePosition;
uniform mat4 u_ModelMatrix;
uniform mat4 u_ViewMatrix;
uniform mat4 u_ProjectionMatrix;
uniform mat4 u_TextureMatrix;
out vec2 v_texCoord;
out float weight[5];
void main(){
    gl_Position = u_ProjectionMatrix * u_ViewMatrix * u_ModelMatrix *a_Position;
    v_texCoord = (u_TextureMatrix*vec4(a_TexturePosition,0,1)).xy;
    weight[0] = 0.152781;
    weight[1] = 0.144599;
    weight[2] = 0.122589;
    weight[3] = 0.093095;
    weight[4] = 0.063327;
}
