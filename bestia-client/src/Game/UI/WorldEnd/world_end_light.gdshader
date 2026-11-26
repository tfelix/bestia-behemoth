shader_type canvas_item;
// The world fills with white light, rising from below and closing in from the sides, with motes of light drifting up
// through it. Once all is white, it settles into a pale sky so the words on it can be read.

// 0 shows the world, 1 is all white.
uniform float progress : hint_range(0.0, 1.0) = 0.0;
// 0 is all white, 1 the pale sky.
uniform float settle : hint_range(0.0, 1.0) = 0.0;
uniform vec4 light : source_color = vec4(1.0, 0.98, 0.93, 1.0);
uniform vec4 mote : source_color = vec4(1.0, 0.9, 0.6, 1.0);
uniform vec4 sky_top : source_color = vec4(0.55, 0.63, 0.8, 1.0);
uniform vec4 sky_bottom : source_color = vec4(0.93, 0.84, 0.66, 1.0);

float hash(vec2 p) {
	return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec2 p) {
	vec2 i = floor(p);
	vec2 f = fract(p);
	vec2 u = f * f * (3.0 - 2.0 * f);
	return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0)), u.x), u.y);
}

float motes(vec2 p, float cells, float speed) {
	vec2 q = p * cells + vec2(0.0, TIME * speed);
	vec2 cell = floor(q);
	float h = hash(cell);
	vec2 at = vec2(hash(cell + 3.1), hash(cell + 7.7)) * 0.6 + 0.2;
	float lit = step(0.8, h) * (0.5 + 0.5 * sin(TIME * 2.0 + h * 30.0));
	return lit * (1.0 - smoothstep(0.03, 0.16, length(fract(q) - at)));
}

void fragment() {
	vec2 aspect = vec2(SCREEN_PIXEL_SIZE.y / SCREEN_PIXEL_SIZE.x, 1.0);
	vec2 p = UV * aspect;

	// Where the light arrives first: low on the screen and at its sides. The centre, where the masters were called
	// away, fills last.
	float from_below = UV.y;
	float from_sides = abs(UV.x - 0.5) * 2.0;
	float first = max(from_below * 0.8, from_sides * 0.6) + (noise(p * 3.0 + vec2(0.0, TIME * 0.2)) - 0.5) * 0.25;
	float reach = mix(-0.5, 1.6, progress);
	float veil = smoothstep(0.0, 0.6, reach - (1.0 - first));

	vec3 sky = mix(sky_top.rgb, sky_bottom.rgb, UV.y);
	vec3 colour = mix(light.rgb, sky, settle);
	// Motes show in the light while it rises, and stay faintly in the sky.
	float glints = (motes(p, 18.0, 0.7) + motes(p, 34.0, 1.1) * 0.6) * mix(veil * (1.0 - veil) * 4.0, 0.35, settle);
	colour = mix(colour, mote.rgb, clamp(glints, 0.0, 1.0));

	COLOR = vec4(colour, clamp(max(veil, glints), 0.0, 1.0));
}
