#version 150

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in vec2 UV2;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;
out vec2 texCoord2;

void main() {
    // 1.21.6: matrices and fog moved to the DynamicTransforms/Projection/Fog uniform blocks; fog shape is gone,
    // vanilla now blends a spherical environmental fog with a cylindrical render distance fog
    vec3 pos = Position + ModelOffset;
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    sphericalVertexDistance = fog_spherical_distance(pos);
    cylindricalVertexDistance = fog_cylindrical_distance(pos);
    vertexColor = Color;
    texCoord0 = UV0;
    texCoord2 = UV2;
}
