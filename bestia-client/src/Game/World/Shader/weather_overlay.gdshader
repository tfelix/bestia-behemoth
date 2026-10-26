// Wet and snow on anything that is not terrain: props, trees, buildings and characters.
//
// Drawn as a material overlay, a second pass over the object's own material, so it works on any mesh without
// knowing its material. One pass carries both effects: snow as white on what faces up, wet as a dark, glossy
// film everywhere else. `WeatherOverlay.cs` attaches it only while there is something to draw.
shader_type spatial;
render_mode blend_mix, depth_draw_never, cull_back, diffuse_burley, specular_schlick_ggx;

global uniform float weather_wetness;
#include "res://Game/World/Shader/weather_snow.gdshaderinc"

// How much of the snow a surface keeps: everything for what stands still, a frost for what walks through it.
uniform float snow_share : hint_range(0.0, 1.0) = 1.0;

// How far snow lying on an upward face lifts it, in metres at full cover.
uniform float cap_metres : hint_range(0.0, 0.3) = 0.05;

uniform vec3 snow_colour : source_color = vec3(0.92, 0.94, 0.96);
uniform float snow_melt_celsius : hint_range(-20.0, 20.0) = 1.0;

// Albedo kept under a full soaking. Same idea as the terrain's `wetness_darkening`.
uniform float wet_darkening : hint_range(0.3, 1.0) = 0.65;

varying vec3 v_world_pos;

float snow_cover(float height) {
	return weather_snow_cover(height, snow_melt_celsius) * snow_share;
}

void vertex() {
	v_world_pos = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xyz;
	float up = normalize((MODEL_MATRIX * vec4(NORMAL, 0.0)).xyz).y;

	VERTEX += NORMAL * cap_metres * snow_cover(v_world_pos.y) * smoothstep(0.3, 0.8, up);
}

void fragment() {
	float up = normalize((INV_VIEW_MATRIX * vec4(NORMAL, 0.0)).xyz).y;

	// A coarse speckle on the edge of the mask, so snow thins out in patches rather than along a clean line.
	float speckle = fract(sin(dot(floor(v_world_pos.xz * 8.0 + v_world_pos.y * 3.0), vec2(12.9898, 78.233))) * 43758.5453);
	float snow = snow_cover(v_world_pos.y) * smoothstep(0.15, 0.55, up + (speckle - 0.5) * 0.3);
	float wet = clamp(weather_wetness, 0.0, 1.0) * (1.0 - snow) * (1.0 - wet_darkening);

	float coverage = snow + wet;
	float snowy = snow / max(coverage, 0.0001);

	ALBEDO = snow_colour * snowy;
	ROUGHNESS = mix(0.12, 0.85, snowy);
	SPECULAR = 0.5;
	ALPHA = clamp(coverage, 0.0, 1.0);
}
