#version 150

// 1.21.9: copy of vanilla's removed core/position_color_tex_lightmap shader, which the no fog fluid pipeline used.
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in vec2 UV2;

out vec4 vertexColor;
out vec2 texCoord0;
out vec2 texCoord2;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    vertexColor = Color;
    texCoord0 = UV0;
    texCoord2 = UV2;
}
