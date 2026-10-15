// One flame on a burning square metre, drawn by `GroundFlames` as a multimesh instance.
//
// INSTANCE_CUSTOM is (ignited at, out at, seed, height), the times on `GroundFireClock`. The quad's UV is
// all the geometry used: the vertex shader builds the billboard itself.

shader_type spatial;

// Premultiplied, so the hot core covers the ground and the thin edge only adds light.
render_mode unshaded, blend_premul_alpha, depth_draw_never, cull_disabled, shadows_disabled, world_vertex_coords;

global uniform float ground_fire_clock;
global uniform float ground_fire_burn_seconds;

// The wind the grass sways in, in m/s and Godot's axes. See `grass.gdshader`.
global uniform vec3 weather_wind;

uniform vec3 core_colour : source_color = vec3(1.0, 0.86, 0.5);
uniform vec3 mid_colour : source_color = vec3(1.0, 0.5, 0.1);
uniform vec3 outer_colour : source_color = vec3(0.75, 0.13, 0.02);

// Width over height of the quad.
uniform float aspect : hint_range(0.2, 2.0) = 0.9;

// How far the flame leans toward the camera's up, so it does not thin to a line under a steep camera.
uniform float camera_lean : hint_range(0.0, 1.0) = 0.45;

uniform float rise_seconds : hint_range(0.05, 3.0) = 0.6;

// Has to end before `ClientGroundFire.KeptAfterOutSeconds` drops the record.
uniform float fade_seconds : hint_range(0.05, 3.0) = 1.4;

// What is left of the flame once its ground has burnt down, as a share of its peak. A grass fire is a
// tall front with smouldering ground behind it, not a field of equal flames.
uniform float burnt_down : hint_range(0.0, 1.0) = 0.0;

// Downwind lean at the tip, in metres per metre of flame, once the wind is strong enough to saturate it.
uniform float wind_max_lean : hint_range(0.0, 2.0) = 1.1;

// The wind speed, in m/s, at which the lean reaches about 63% of `wind_max_lean`.
uniform float wind_reference_speed : hint_range(0.5, 40.0) = 8.0;

uniform float scroll_speed : hint_range(0.0, 6.0) = 1.6;
uniform float opacity : hint_range(0.0, 1.0) = 0.85;

varying flat float v_seed;
varying flat float v_life;

float hash12(vec2 p) {
	vec3 p3 = fract(vec3(p.xyx) * 0.1031);
	p3 += dot(p3, p3.yzx + 33.33);
	return fract((p3.x + p3.y) * p3.z);
}

float value_noise(vec2 p) {
	vec2 i = floor(p);
	vec2 f = fract(p);
	vec2 u = f * f * (3.0 - 2.0 * f);
	return mix(
		mix(hash12(i), hash12(i + vec2(1.0, 0.0)), u.x),
		mix(hash12(i + vec2(0.0, 1.0)), hash12(i + vec2(1.0, 1.0)), u.x),
		u.y);
}

float fbm(vec2 p) {
	float sum = 0.0;
	float amplitude = 0.5;
	for (int octave = 0; octave < 4; octave++) {
		sum += amplitude * value_noise(p);
		p = p * 2.03 + vec2(1.7, 9.2);
		amplitude *= 0.5;
	}
	return sum / 0.9375;
}

void vertex() {
	float ignited = INSTANCE_CUSTOM.x;
	float out_at = INSTANCE_CUSTOM.y;
	v_seed = INSTANCE_CUSTOM.z;

	float age = ground_fire_clock - ignited;
	float rise = smoothstep(0.0, rise_seconds, age);
	float fade = out_at > 0.0 ? 1.0 - smoothstep(0.0, fade_seconds, ground_fire_clock - out_at) : 1.0;
	float fuel = mix(1.0, burnt_down, smoothstep(0.2, 0.8, age / max(ground_fire_burn_seconds, 0.001)));
	v_life = rise * fade * fuel;

	float flicker = 1.0 + 0.10 * sin(TIME * 9.0 + v_seed * 40.0) + 0.07 * sin(TIME * 23.0 + v_seed * 13.0);
	float height = INSTANCE_CUSTOM.w * mix(0.3, 1.0, v_life) * flicker * step(0.001, v_life);

	vec3 right = INV_VIEW_MATRIX[0].xyz;
	vec3 up = normalize(mix(vec3(0.0, 1.0, 0.0), INV_VIEW_MATRIX[1].xyz, camera_lean));

	// Bent rather than tilted, so the base stays on its ground and the tip goes furthest.
	float speed = length(weather_wind.xz);
	vec3 downwind = vec3(weather_wind.x, 0.0, weather_wind.z) / max(speed, 0.001);
	float gust = 1.0 + 0.2 * sin(TIME * 2.7 + v_seed * 20.0);
	float lean = wind_max_lean * (1.0 - exp(-speed / max(wind_reference_speed, 0.001))) * gust;
	float along = 1.0 - UV.y;

	VERTEX = MODEL_MATRIX[3].xyz + right * (UV.x - 0.5) * height * aspect + up * along * height
		+ downwind * lean * height * along * along;
}

void fragment() {
	float v = 1.0 - UV.y;
	float x = UV.x * 2.0 - 1.0;
	vec2 p = vec2(UV.x * 2.2 + v_seed * 31.0, v * 1.8 - TIME * scroll_speed + v_seed * 7.0);

	// The upper flame licks sideways; the noise then eats it into tongues.
	x += (fbm(p * 1.1 + 4.7) - 0.5) * 1.1 * v;
	float n = fbm(p * 2.0);

	float half_width = mix(0.8, 0.08, v);
	float body = 1.0 - smoothstep(half_width * 0.35, half_width, abs(x));
	float fire = body * (1.0 - v * v) * 1.35 - (1.0 - n) * (0.25 + 0.75 * v);

	float coverage = smoothstep(-0.05, 0.35, fire) * smoothstep(0.0, 0.08, v) * smoothstep(0.0, 0.5, v_life);

	vec3 colour = mix(outer_colour, mid_colour, smoothstep(0.1, 0.5, fire));
	colour = mix(colour, core_colour, smoothstep(0.65, 1.0, fire));

	// The outer flame mostly adds light; only the hotter body hides what is behind it.
	ALBEDO = colour * coverage;
	ALPHA = coverage * opacity * mix(0.3, 1.0, smoothstep(0.15, 0.6, fire));
}
