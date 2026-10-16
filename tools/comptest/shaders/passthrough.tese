#version 450
layout(triangles, equal_spacing, cw) in;
layout(location=0) in vec3 inColor[];
layout(location=0) out vec3 outColor;
void main() {
    vec3 p0 = gl_TessCoord.x * gl_in[0].gl_Position.xyz;
    vec3 p1 = gl_TessCoord.y * gl_in[1].gl_Position.xyz;
    vec3 p2 = gl_TessCoord.z * gl_in[2].gl_Position.xyz;
    gl_Position = vec4(p0 + p1 + p2, 1.0);
    vec3 c0 = gl_TessCoord.x * inColor[0];
    vec3 c1 = gl_TessCoord.y * inColor[1];
    vec3 c2 = gl_TessCoord.z * inColor[2];
    outColor = c0 + c1 + c2;
}
