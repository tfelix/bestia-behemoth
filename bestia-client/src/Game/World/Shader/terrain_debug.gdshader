shader_type spatial;

render_mode cull_back, diffuse_burley;

// The terrain shader with its intermediate values brought to the surface.
//
// A separate shader rather than a uniform on the real one, so that the shipping path carries none of this: a
// debug_view branch keeps every view's arithmetic and every view's texture fetch compiled into the hot loop,
// and terrain is already the most register-bound thing on screen. Both include the same file, so the values
// shown here are the values used there and cannot drift from them.
//
// TerrainRenderer swaps between the two materials at runtime, so this is a key press rather than a rebuild.
#include "res://Game/World/Shader/terrain_common.gdshaderinc"

group_uniforms Debug;

// Off is the shipping shader exactly. Every other view is drawn as emission so that it reads the same in a
// cave at midnight as on a hillside at noon - a debug view that is itself lit is a debug view that lies.
uniform int debug_view : hint_enum(
	"Off",
	"Slot (dominant)",
	"Slot (blended)",
	"Splat weights",
	"Slope",
	"Triplanar axis",
	"Tint",
	"World normal",
	"Texel density",
	"Height channel") = 0;

group_uniforms;

// One colour per slot, in BlockAppearance.SurfaceSlot order. Chosen to be told apart at a glance and
// deliberately unlike the materials they stand for - a debug view that looks like the thing it is debugging is
// one you cannot use to tell whether it is working.
const vec3 SLOT_COLOURS[8] = {
	vec3(0.50, 0.50, 0.50), // NEUTRAL   grey
	vec3(0.10, 0.90, 0.20), // GRASS     green
	vec3(0.85, 0.85, 0.10), // DRY_GRASS yellow
	vec3(1.00, 0.45, 0.00), // SAND      orange
	vec3(0.55, 0.25, 0.05), // SOIL      brown
	vec3(0.20, 0.45, 1.00), // ROCK      blue
	vec3(1.00, 1.00, 1.00), // SNOW      white
	vec3(1.00, 0.00, 0.80)  // RESERVED  magenta, because seeing it at all is the bug
};

vec3 debug_colour(TerrainSurface surface) {
	if (debug_view == 1) {
		return SLOT_COLOURS[surface.slot0];
	}

	if (debug_view == 2) {
		return mix(SLOT_COLOURS[surface.slot1], SLOT_COLOURS[surface.slot0], surface.slot0_share);
	}

	// The four heaviest channels straight out of the vertex attributes, so a mesher bug and a shader bug look
	// different: this view ignores the top-two selection entirely.
	if (debug_view == 3) {
		return vec3(
			v_weights_low.g + v_weights_high.g,
			v_weights_low.b + v_weights_high.b,
			v_weights_low.r + v_weights_high.r);
	}

	if (debug_view == 4) {
		return vec3(surface.slope);
	}

	// Red, green, blue for the x, y and z projections. Flat ground should be almost entirely green; a cliff
	// that is not almost entirely red or blue means triplanar_sharpness is too low.
	if (debug_view == 5) {
		return surface.axis_blend;
	}

	if (debug_view == 6) {
		return surface.tint;
	}

	if (debug_view == 7) {
		return normalize(v_world_normal) * 0.5 + 0.5;
	}

	// How fast the world-space UV changes per pixel, which is what picks the mip level. Should shade smoothly
	// with distance; anything speckled here is the fp32 precision failure slot_uv_origin exists to prevent, and
	// it will be shimmering in the shipping shader for the same reason.
	if (debug_view == 8) {
		float density = length(dFdx(v_world_pos)) + length(dFdy(v_world_pos));

		return vec3(fract(log2(max(density, 0.0001)) * 0.5 + 0.5));
	}

	if (debug_view == 9) {
		return vec3(terrain_sample_slot(surface.slot0, dFdx(v_world_pos), dFdy(v_world_pos), surface.axis_blend).a);
	}

	return vec3(0.0);
}

// Repeated verbatim from terrain.gdshader; see terrain_common.gdshaderinc for why it cannot be shared.
void vertex() {
	v_world_pos = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xyz;
	v_world_normal = normalize(MODEL_NORMAL_MATRIX * NORMAL);
	v_weights_low = CUSTOM0;
	v_weights_high = CUSTOM1;
}

void fragment() {
	TerrainSurface surface = terrain_surface(COLOR.rgb);

	if (debug_view == 0) {
		ALBEDO = surface.albedo;
		ROUGHNESS = surface.roughness;
		SPECULAR = 0.0;
	} else {
		ALBEDO = vec3(0.0);
		EMISSION = debug_colour(surface);
		ROUGHNESS = 1.0;
		SPECULAR = 0.0;
	}
}
