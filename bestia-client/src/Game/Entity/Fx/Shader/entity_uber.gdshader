// The one material every character body is drawn with: its own look, the weather on it, and every entity effect.
//
// The base look is set per material in the editor, like a StandardMaterial3D. The `fx_*` knobs are instance
// uniforms, set per body by `EntityFx`, so a hundred masters share one material per colour and still fade
// independently. With every knob at its default the body looks exactly like its base material.
//
// One pass rather than an overlay: an overlay draws on top and cannot make the body under it disappear.
shader_type spatial;
render_mode cull_disabled, depth_draw_opaque, diffuse_burley, specular_schlick_ggx;

#include "res://Game/World/Shader/weather_cover.gdshaderinc"

group_uniforms base;
uniform vec4 albedo_colour : source_color = vec4(1.0);
uniform sampler2D albedo_texture : source_color, filter_linear_mipmap, repeat_enable, hint_default_white;
uniform float metallic : hint_range(0.0, 1.0) = 0.0;
uniform float roughness : hint_range(0.0, 1.0) = 0.5;
uniform sampler2D normal_texture : hint_normal, filter_linear_mipmap, repeat_enable;
// 0 leaves the normal map out, which is what every model without one wants.
uniform float normal_strength : hint_range(0.0, 2.0) = 0.0;
uniform vec3 emission_colour : source_color = vec3(0.0);
uniform float emission_energy : hint_range(0.0, 16.0) = 0.0;
// Texels less opaque than this are cut out, for hair cards and the like. 0 keeps everything.
uniform float alpha_scissor : hint_range(0.0, 1.0) = 0.0;

group_uniforms weather;
// Things that move through snow only frost over, so they keep less of it than a roof does.
uniform float snow_share : hint_range(0.0, 1.0) = 0.3;
uniform vec3 snow_colour : source_color = vec3(0.92, 0.94, 0.96);
uniform float snow_melt_celsius : hint_range(-20.0, 20.0) = 1.0;
uniform float wet_darkening : hint_range(0.3, 1.0) = 0.65;

group_uniforms dissolve;
// World metres per noise cell; smaller cells give a finer, grainier edge.
uniform float dissolve_noise_scale : hint_range(0.5, 40.0) = 9.0;
// How far the noise lets the edge stray from a straight sweep, as a share of the body.
uniform float dissolve_noise_share : hint_range(0.0, 1.0) = 0.35;
// Width of the glowing rim along the edge, as a share of the sweep.
uniform float dissolve_edge : hint_range(0.0, 0.3) = 0.08;
group_uniforms;

// A colour laid over the base look; alpha is how much.
instance uniform vec4 fx_tint = vec4(1.0, 1.0, 1.0, 0.0);
// A fresnel glow around the outline.
instance uniform vec4 fx_rim_colour = vec4(1.0);
instance uniform float fx_rim = 0.0;
// A white flash, as on a hit.
instance uniform float fx_flash = 0.0;
// 1 replaces the look with a flat body glowing in `fx_glow_colour`.
instance uniform float fx_silhouette = 0.0;
instance uniform vec4 fx_glow_colour = vec4(0.7, 0.85, 1.0, 1.0);
instance uniform float fx_glow = 0.0;
// 0 is whole, 1 is gone. The edge sweeps along `fx_dissolve_dir` across `fx_extent` metres around `fx_origin`,
// the body's centre in world space.
instance uniform float fx_dissolve = 0.0;
instance uniform vec3 fx_dissolve_dir = vec3(0.0, 1.0, 0.0);
instance uniform vec3 fx_origin = vec3(0.0);
instance uniform float fx_extent = 2.0;

varying vec3 v_world_pos;
varying vec3 v_local_pos;

float hash(vec3 p) {
	return fract(sin(dot(p, vec3(127.1, 311.7, 74.7))) * 43758.5453);
}

float value_noise(vec3 p) {
	vec3 cell = floor(p);
	vec3 f = fract(p);
	f = f * f * (3.0 - 2.0 * f);

	return mix(
		mix(mix(hash(cell), hash(cell + vec3(1, 0, 0)), f.x),
			mix(hash(cell + vec3(0, 1, 0)), hash(cell + vec3(1, 1, 0)), f.x), f.y),
		mix(mix(hash(cell + vec3(0, 0, 1)), hash(cell + vec3(1, 0, 1)), f.x),
			mix(hash(cell + vec3(0, 1, 1)), hash(cell + vec3(1, 1, 1)), f.x), f.y),
		f.z);
}

// Where along the sweep this pixel lies, 0 to 1, roughened by noise. A pixel is gone once fx_dissolve passes it.
float dissolve_position() {
	float along = dot(v_world_pos - fx_origin, normalize(fx_dissolve_dir)) / max(fx_extent, 0.01) + 0.5;
	// The noise is taken in the model's own space, so the pattern sticks to the body rather than the world.
	float noise = value_noise(v_local_pos * dissolve_noise_scale);
	return mix(clamp(along, 0.0, 1.0), noise, dissolve_noise_share);
}

void vertex() {
	v_local_pos = VERTEX;
	v_world_pos = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xyz;
}

void fragment() {
	vec4 texel = texture(albedo_texture, UV) * albedo_colour;
	if (texel.a < alpha_scissor) {
		discard;
	}

	// Scaled so the last pixel goes exactly at 1, and nothing at all goes at 0.
	float sweep = fx_dissolve * (1.0 + dissolve_edge);
	float position = dissolve_position();
	if (fx_dissolve > 0.0 && position < sweep - dissolve_edge) {
		discard;
	}
	float edge = fx_dissolve > 0.0 ? 1.0 - smoothstep(sweep - dissolve_edge, sweep, position) : 0.0;

	vec3 albedo = texel.rgb;
	float rough = roughness;

	float up = normalize((INV_VIEW_MATRIX * vec4(NORMAL, 0.0)).xyz).y;
	vec2 cover = weather_cover(v_world_pos, up, snow_share, snow_melt_celsius);
	albedo = mix(albedo * mix(1.0, wet_darkening, cover.y), snow_colour, cover.x);
	rough = mix(mix(rough, 0.12, cover.y), 0.85, cover.x);

	albedo = mix(albedo, albedo * fx_tint.rgb, fx_tint.a);
	albedo = mix(albedo, vec3(1.0), fx_flash * 0.6);

	float fresnel = pow(1.0 - clamp(dot(NORMAL, VIEW), 0.0, 1.0), 3.0);
	// Even across the body so it reads as a flat shape, whitening towards the outline.
	vec3 glow = mix(fx_glow_colour.rgb * 1.2, vec3(1.6), fresnel) * fx_glow;

	vec3 emission = emission_colour * emission_energy;
	emission += fx_rim_colour.rgb * fx_rim * fresnel;
	emission += vec3(fx_flash);
	emission = mix(emission, glow, fx_silhouette);
	// Only a glowing effect lights its edge; a plain fade, like a death, just eats the body away.
	emission += fx_glow_colour.rgb * fx_glow * edge * 4.0;

	ALBEDO = mix(albedo, vec3(0.0), fx_silhouette);
	METALLIC = metallic * (1.0 - fx_silhouette);
	ROUGHNESS = mix(rough, 1.0, fx_silhouette);
	SPECULAR = 0.5 * (1.0 - fx_silhouette);
	EMISSION = emission;

	if (normal_strength > 0.0) {
		NORMAL_MAP = texture(normal_texture, UV).rgb;
		NORMAL_MAP_DEPTH = normal_strength;
	}
}
