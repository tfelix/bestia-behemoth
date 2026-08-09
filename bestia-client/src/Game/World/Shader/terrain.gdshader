shader_type spatial;

// Terrain is a closed surface seen from outside, so back faces are never wanted; and Burley over Lambert
// because the whole subject is rough dielectrics, which is the case the two disagree about.
render_mode cull_back, diffuse_burley;

#include "res://Game/World/Shader/terrain_common.gdshaderinc"

// Repeated verbatim in terrain_debug.gdshader; see terrain_common.gdshaderinc for why it cannot be shared.
void vertex() {
	v_world_pos = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xyz;
	v_world_normal = normalize(MODEL_NORMAL_MATRIX * NORMAL);
	v_weights_low = CUSTOM0;
	v_weights_high = CUSTOM1;
}

void fragment() {
	TerrainSurface surface = terrain_surface(COLOR.rgb);

	ALBEDO = surface.albedo;
	ROUGHNESS = surface.roughness;

	// Zero rather than left alone. The scene's sky is near-white and terrain is mostly large and flat, so any
	// specular at all is a sheet of sky reflected back into the camera - which reads on screen as "the material
	// did not apply", because it is indistinguishable from Godot's white fallback.
	SPECULAR = 0.0;
}
