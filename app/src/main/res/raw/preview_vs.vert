#version 300 es

layout(location = 0) in vec4 a_Position;
layout(location = 1) in vec2 a_TexturePosition;
uniform mat4 u_ModelMatrix;
uniform mat4 u_ViewMatrix;
uniform mat4 u_ProjectionMatrix;
uniform mat4 u_TextureMatrix;
out vec2 v_texCoord;
void main(){
    gl_Position = u_ProjectionMatrix * u_ViewMatrix * u_ModelMatrix *a_Position;
    v_texCoord = (u_TextureMatrix*vec4(a_TexturePosition,0,1)).xy;
}
