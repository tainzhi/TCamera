#version 300 es
precision mediump float;

in vec4 v_Color;
out vec4 outColor;
uniform float u_Opacity;
void main() {
    outColor = v_Color * u_Opacity;
}