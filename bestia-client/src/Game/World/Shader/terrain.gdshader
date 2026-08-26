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
	v_weights_low2 = CUSTOM2;
	v_weights_high2 = CUSTOM3;
}

void fragment() {
	TerrainSurface surface = terrain_surface(COLOR.rgb);

	ALBEDO = surface.albedo;
	ROUGHNESS = surface.roughness;
	AO = surface.ao;
	// AO is a texture-space term - the crevices between pebbles and the gaps under grass, baked into the
	// material's alpha channel. It belongs to the ambient, which is what Godot's default of 0 says: whether
	// the sun reaches a fragment is already answered, per fragment, by the shadow map. Feeding AO into the
	// direct light as well darkened every lit surface by its own texture and took the crisp edge off the
	// sun/shade boundary on slopes - relief bought by flattening the thing the relief was meant to show.
	AO_LIGHT_AFFECT = 0.0;

	// NORMAL and never NORMAL_MAP.
	//
	// Godot overwrites NORMAL after fragment() whenever NORMAL_MAP has been written, using TANGENT and BINORMAL
	// to build the frame - and this mesh has neither, because surface nets emits no tangents. Writing both would
	// silently discard everything the triplanar blend just computed and shade the world flat, with no error to
	// say so. Fragment-stage NORMAL is view space, hence the matrix.
	NORMAL = normalize((VIEW_MATRIX * vec4(surface.world_normal, 0.0)).xyz);

	// Zero rather than left alone. The scene's sky is near-white and terrain is mostly large and flat, so any
	// specular at all is a sheet of sky reflected back into the camera - which reads on screen as "the material
	// did not apply", because it is indistinguishable from Godot's white fallback.
	SPECULAR = 0.0;
}
