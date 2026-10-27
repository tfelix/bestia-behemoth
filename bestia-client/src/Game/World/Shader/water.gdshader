shader_type spatial;

// Two-sided because a swimming player looks up at the back of this one sheet. `depth_draw_never` because the
// whole shader reads what stands behind the water, and a surface that writes depth cannot see past itself.
render_mode blend_mix, depth_draw_never, cull_disabled, diffuse_burley, specular_schlick_ggx;

// `filter_nearest`: a depth value indexes a scene, and blending two across a silhouette invents a halo of the
// wrong depth around everything the water stands behind.
uniform sampler2D depth_texture : hint_depth_texture, repeat_disable, filter_nearest;

// The opaque scene, which the reflection samples.
uniform sampler2D screen_texture : hint_screen_texture, repeat_disable, filter_linear_mipmap;

group_uniforms Colour;

// Sunny-day colours. The bed shows through shallow water; deep water is what remains after absorption.
uniform vec3 shallow_colour : source_color = vec3(0.3, 0.8, 0.84);
uniform vec3 deep_colour : source_color = vec3(0, 0.42, 0.66);

// What the water turns to under a full overcast: grey-green and darker.
uniform vec3 storm_shallow_colour : source_color = vec3(0.45, 0.56, 0.56);
uniform vec3 storm_deep_colour : source_color = vec3(0.15, 0.25, 0.29);

// How far toward the storm colours a full overcast goes. Zero keeps the sunny colours in every weather.
uniform float storm_tint : hint_range(0.0, 1.0) = 0.85;

// Cloud cover below this leaves the colour alone, so a few clouds on a fine day do not grey the sea. Above a
// cloudy sky's floor of 0.55, so a dry grey day keeps the water blue and only rain clouds turn it.
uniform float storm_tint_start : hint_range(0.0, 1.0) = 0.6;

// Metres of water column before the colour is mostly `deep_colour`. Exponential, so a scale length.
uniform float absorption_distance : hint_range(0.2, 40.0) = 2.4;

// Caps the column where nothing has streamed in behind the water and the depth buffer holds the far plane.
uniform float max_depth : hint_range(1.0, 200.0) = 30.0;

// Shorter than `absorption_distance`: water hides the bed sooner than it finishes changing colour.
uniform float opacity_distance : hint_range(0.1, 20.0) = 0.6;

// Below one, so a shelf and a trench still read differently.
uniform float max_opacity : hint_range(0.1, 1.0) = 0.92;

// How much a storm stirs the water up and hides the bed, scaled by the wind like the waves.
uniform float storm_murk : hint_range(0.0, 1.0) = 0.7;

group_uniforms;

group_uniforms Foam;

uniform vec3 foam_colour : source_color = vec3(0.95, 0.98, 1.0);

// Water column in metres over which the shore foam fades out.
uniform float foam_distance : hint_range(0.05, 4.0) = 0.35;

// Width of the soft edge on every foam shape. Small keeps the cartoon look; zero would alias.
uniform float foam_softness : hint_range(0.005, 0.3) = 0.04;

// Soft wisps of foam lying along the shore beyond the band, drifting in toward it.
uniform float streak_strength : hint_range(0.0, 1.0) = 0.85;

// Streaks per foam width, and how many foam widths out from the shore they reach.
uniform float streak_density : hint_range(0.5, 6.0) = 2.0;
uniform float streak_reach : hint_range(1.0, 8.0) = 2.4;

// Width of one streak as a fraction of the gap between two. Wider reads softer.
uniform float streak_width : hint_range(0.02, 0.5) = 0.2;

uniform float streak_speed : hint_range(0.0, 1.0) = 0.12;

// A whitish wash over the shallows just past the band, so the foam fades into the water instead of ending.
uniform float shore_glow : hint_range(0.0, 1.0) = 0.15;

// Foam on wave crests in a storm, scaled by the same wind factor as the waves.
uniform float whitecaps : hint_range(0.0, 1.0) = 0.6;

group_uniforms;

group_uniforms Waves;

// A seamless noise texture from water.tres, not an imported asset.
uniform sampler2D wave_noise : repeat_enable, filter_linear_mipmap;

// Metres per tile of the noise. A second layer at a fraction of it adds chop to the swell.
uniform float wave_scale : hint_range(2.0, 200.0) = 22.0;

uniform float wave_speed : hint_range(0.0, 1.0) = 0.045;

// How far the normal tilts in calm air and at `storm_wind_speed`. Normals only: the server's water height
// decides wading and swimming, so the sheet must stay where the mesher put it.
uniform float wave_strength : hint_range(0.0, 2.0) = 0.1;
uniform float storm_wave_strength : hint_range(0.0, 2.0) = 1.1;

// Wind speeds in m/s, read from `weather_wind`, between which the waves grow from calm to storm. High, because
// the server's wind is 3 + 22 m/s of gust and averages near 14: a storm at 16 had every other dry, grey day
// throwing whitecaps across the whole sea.
uniform float calm_wind_speed : hint_range(0.0, 40.0) = 8.0;
uniform float storm_wind_speed : hint_range(0.0, 40.0) = 28.0;

// Fraction of the wind's drift the ripples travel with, so storm waves run downwind.
uniform float wind_follow : hint_range(0.0, 0.2) = 0.03;

// Extra chop from rain, read from the shared wetness global.
uniform float rain_chop : hint_range(0.0, 1.0) = 0.45;

group_uniforms;

group_uniforms Reflection;

// How much of the scene above the water shows mirrored in it. Zero skips the trace.
uniform float reflection_strength : hint_range(0.0, 1.0) = 0.3;

// How far the waves bend the reflected ray. Zero is a mirror.
uniform float reflection_distortion : hint_range(0.0, 1.0) = 0.35;

// Metres the ray may travel before it gives up. Longer finds far banks at the price of coarser steps.
uniform float reflection_distance : hint_range(1.0, 100.0) = 25.0;

group_uniforms;

group_uniforms Sheen;

uniform float water_roughness : hint_range(0.0, 0.5) = 0.06;

// Modest, because a mirror-smooth sheet reflecting the sky turns the sea white.
uniform float sheen_strength : hint_range(0.0, 1.0) = 0.35;

uniform float fresnel_power : hint_range(1.0, 8.0) = 4.0;

group_uniforms;

// Driven by WeatherState, as globals so water, foliage and terrain read the same numbers.
global uniform float weather_wetness;
global uniform float weather_overcast;
global uniform vec3 weather_wind;
global uniform vec2 weather_gust_drift;

// Steps of the reflection trace, then halvings to place the hit between the last two steps.
const int REFLECTION_STEPS = 32;
const int REFLECTION_REFINES = 6;

varying vec3 v_world_pos;
varying vec3 v_world_normal;

void vertex() {
	v_world_pos = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xyz;
	v_world_normal = (MODEL_MATRIX * vec4(NORMAL, 0.0)).xyz;
}

// 0 in calm air, 1 at `storm_wind_speed`.
float storminess() {
	return smoothstep(calm_wind_speed, storm_wind_speed, length(weather_wind.xz));
}

// Noise UV for one wave layer. The wind drift is folded per layer with `fract`, which the seamless noise hides,
// so a long storm does not grow the coordinates until float precision smears them.
vec2 wave_uv(vec2 world_xz, float scale, vec2 flow, float time) {
	vec2 drift = fract(weather_gust_drift * wind_follow / scale);
	return world_xz / scale - drift + flow * time * wave_speed;
}

// Two layers of the same noise at different scales and headings, so neither the tiling nor the motion repeats.
float wave_height(vec2 world_xz, float time, float chop) {
	float swell = texture(wave_noise, wave_uv(world_xz, wave_scale, vec2(1.0, 0.6), time)).r;
	float ripple = texture(wave_noise, wave_uv(world_xz, wave_scale * 0.27, vec2(-1.7, 1.1), time)).r;
	return swell + chop * ripple;
}

// The view-space position of the opaque surface at a screen UV.
vec3 scene_position(vec2 uv, mat4 inv_projection) {
	float raw_depth = texture(depth_texture, uv).x;
	vec4 position = inv_projection * vec4(uv * 2.0 - 1.0, raw_depth, 1.0);
	return position.xyz / position.w;
}

vec2 to_screen(vec3 view_position, mat4 projection) {
	vec4 clip = projection * vec4(view_position, 1.0);
	return clip.xy / clip.w * 0.5 + 0.5;
}

// Screen-space reflection: marches the ray through the depth buffer until it passes behind an opaque surface.
// Returns the hit UV and a weight that fades toward the screen edge and the end of the ray, where the screen
// holds nothing to reflect and a hard cut would show.
vec3 trace_reflection(vec3 origin, vec3 direction, mat4 projection, mat4 inv_projection) {
	// Growing steps: near hits (a rock at the waterline) need precision, far ones (the bank) only need reach.
	float growth = 1.12;
	float step_length = reflection_distance * (growth - 1.0) / (pow(growth, float(REFLECTION_STEPS)) - 1.0);

	vec3 previous = origin;
	vec3 point = origin;

	for (int i = 0; i < REFLECTION_STEPS; i++) {
		point += direction * step_length;

		// Behind the camera nothing is on screen to find.
		if (point.z > -0.05) {
			break;
		}

		vec2 uv = to_screen(point, projection);
		if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
			break;
		}

		// View space looks down -z, so the ray is behind the scene when it is further from the camera than it.
		float behind = scene_position(uv, inv_projection).z - point.z;

		// Thicker than one step, or a thin object between two samples would let the ray through. Not much
		// thicker, or the ray "hits" things that only stand in front of it, like the bank behind a rock.
		if (behind > 0.0 && behind < step_length * 2.0 + 0.3) {
			for (int r = 0; r < REFLECTION_REFINES; r++) {
				vec3 middle = (previous + point) * 0.5;
				if (scene_position(to_screen(middle, projection), inv_projection).z > middle.z) {
					point = middle;
				} else {
					previous = middle;
				}
			}

			vec2 hit = to_screen(point, projection);
			vec2 edge = min(hit, 1.0 - hit);
			float screen_fade = smoothstep(0.0, 0.08, min(edge.x, edge.y));
			float reach_fade = 1.0 - smoothstep(0.75, 1.0, float(i) / float(REFLECTION_STEPS));
			return vec3(hit, screen_fade * reach_fade);
		}

		previous = point;
		step_length *= growth;
	}

	return vec3(0.0);
}

void fragment() {
	// Water column measured straight down from the surface to whatever is behind it, not along the view ray:
	// the slant distance saturates on faces seen edge on and changes the shoreline with the viewing angle.
	vec3 behind_view = scene_position(SCREEN_UV, INV_PROJECTION_MATRIX);
	vec3 behind_world = (INV_VIEW_MATRIX * vec4(behind_view, 1.0)).xyz;
	float water_depth = clamp(v_world_pos.y - behind_world.y, 0.0, max_depth);

	float storm = storminess();
	float wetness = clamp(weather_wetness, 0.0, 1.0);
	float chop = 0.2 + rain_chop * wetness + 0.55 * storm;

	// Central differences on the height field: cheaper than a normal map and needs no tangents, which
	// surface nets does not emit.
	float texel = wave_scale * 0.02;
	float here = wave_height(v_world_pos.xz, TIME, chop);
	float left = wave_height(v_world_pos.xz - vec2(texel, 0.0), TIME, chop);
	float right = wave_height(v_world_pos.xz + vec2(texel, 0.0), TIME, chop);
	float back = wave_height(v_world_pos.xz - vec2(0.0, texel), TIME, chop);
	float front = wave_height(v_world_pos.xz + vec2(0.0, texel), TIME, chop);

	float tilt = mix(wave_strength, storm_wave_strength, storm);
	vec2 slope = vec2(left - right, back - front);
	vec3 wave_normal = normalize(vec3(slope.x * tilt, 1.0, slope.y * tilt));

	// 1 on the level surface, 0 on a side face (a river step, water over lower ground). Walls keep the mesh
	// normal and get no foam, reflection or Fresnel, or they draw as pale grey sheets.
	vec3 mesh_normal = normalize(v_world_normal);
	float level_face = smoothstep(0.35, 0.75, abs(mesh_normal.y));
	vec3 world_normal = normalize(mix(mesh_normal, wave_normal, level_face));

	// A back face is the underside seen by a submerged camera, and its normal points the other way.
	if (!FRONT_FACING) {
		world_normal = -world_normal;
	}

	float gloom = smoothstep(storm_tint_start, 1.0, clamp(weather_overcast, 0.0, 1.0)) * storm_tint;
	vec3 shallow = mix(shallow_colour, storm_shallow_colour, gloom);
	vec3 deep = mix(deep_colour, storm_deep_colour, gloom);

	// Colour settles more slowly than opacity, which keeps a shallow shelf readable as light rather than thin.
	float tint = 1.0 - exp(-water_depth / absorption_distance);
	float body = 1.0 - exp(-water_depth / opacity_distance);

	vec3 albedo = mix(shallow, deep, tint);
	float alpha = mix(body, 1.0, storm * storm_murk) * max_opacity;

	vec3 view_direction = normalize(-VERTEX);
	vec3 view_normal = normalize((VIEW_MATRIX * vec4(world_normal, 0.0)).xyz);
	float fresnel = pow(1.0 - clamp(dot(view_normal, view_direction), 0.0, 1.0), fresnel_power);

	// Grazing angles hide the bed whatever the depth, which keeps a wide sea from fading out at the horizon.
	alpha = clamp(alpha + fresnel * level_face * (1.0 - alpha) * sheen_strength, 0.0, 1.0);

	// Foam. The band and the whitecaps are hard-edged shapes cut from noise; the streaks are soft. The breakup
	// noise is finer than the waves so the band is ragged rather than a contour of the depth.
	float breakup = texture(wave_noise, wave_uv(v_world_pos.xz, wave_scale * 0.12, vec2(2.0, 0.0), TIME)).r;
	float shore = 1.0 - clamp(water_depth / foam_distance, 0.0, 1.0);
	float band = smoothstep(0.5 - foam_softness, 0.5 + foam_softness, shore + (breakup - 0.5) * 1.1);

	// Streaks follow the depth contours, so they lie along any shore without knowing its direction. Coarse
	// noise bends the contours and thins them out in patches; nothing is cut hard, so no dash ends show.
	float bend = texture(wave_noise, wave_uv(v_world_pos.xz, wave_scale * 0.3, vec2(0.0, 1.0), TIME)).r;
	float patches = texture(wave_noise, wave_uv(v_world_pos.xz, wave_scale * 0.45, vec2(-0.5, 0.0), TIME)).r;
	float foam_widths = water_depth / foam_distance + (bend - 0.5) * 1.2;
	float phase = fract(foam_widths * streak_density + TIME * streak_speed);
	float streak_core = smoothstep(streak_width, streak_width * 0.25, abs(phase - 0.5));
	float streak_fade = smoothstep(0.7, 1.0, foam_widths) * (1.0 - smoothstep(1.0, streak_reach, foam_widths));
	float streaks = streak_core * streak_fade * smoothstep(0.4, 0.75, patches) * streak_strength;

	float glow = (1.0 - smoothstep(0.0, 2.5, water_depth / foam_distance)) * shore_glow;

	float crest = smoothstep(0.82 - foam_softness, 0.82 + foam_softness, here / (1.0 + chop) + breakup * 0.25);
	// Only the top of the storm range breaks, so a dry gale ruffles the water without flecking all of it.
	float caps = crest * smoothstep(0.7, 1.0, storm) * whitecaps;

	float foam = clamp(max(max(band, streaks), max(caps, glow)), 0.0, 1.0) * level_face;
	albedo = mix(albedo, foam_colour, foam);
	alpha = max(alpha, foam);

	// Reflection weight: more at grazing angles, like the sky in the sheen, and none under foam.
	vec3 reflected = vec3(0.0);
	float reflection = 0.0;
	if (reflection_strength > 0.0 && FRONT_FACING) {
		vec3 flat_normal = normalize((VIEW_MATRIX * vec4(mesh_normal, 0.0)).xyz);
		vec3 bent_normal = normalize(mix(flat_normal, view_normal, reflection_distortion));
		vec3 ray = reflect(-view_direction, bent_normal);

		vec3 hit = trace_reflection(VERTEX, ray, PROJECTION_MATRIX, INV_PROJECTION_MATRIX);
		reflected = textureLod(screen_texture, hit.xy, 0.0).rgb;
		reflection = reflection_strength * hit.z * mix(0.6, 1.0, fresnel) * level_face * (1.0 - foam);
	}

	// The reflection is light the surface sends back, so it adds coverage as well as colour. Splitting the
	// blend this way keeps reflection, water and background weights summing to one.
	float covered = alpha + reflection * (1.0 - alpha);
	float inverse_covered = 1.0 / max(covered, 0.001);

	ALBEDO = albedo * alpha * (1.0 - reflection) * inverse_covered;
	EMISSION = reflected * reflection * inverse_covered;
	ALPHA = covered;
	NORMAL = view_normal;
	ROUGHNESS = water_roughness;
	SPECULAR = sheen_strength * (1.0 - foam);
	METALLIC = 0.0;
}
