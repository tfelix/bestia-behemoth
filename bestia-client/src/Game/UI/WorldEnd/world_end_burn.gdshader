shader_type canvas_item;
// The view burns away from the screen's edges to its centre: a scorched rim runs ahead of a ragged band of
// fire, and behind it lies char with glowing specks, until everything is black.

// 0 is untouched, 1 is all black.
uniform float progress : hint_range(0.0, 1.0) = 0.0;
// How wide the burning band is, as a share of the way from the edge to the centre.
uniform float band = 0.1;
uniform vec4 fire_hot : source_color = vec4(1.0, 0.86, 0.45, 1.0);
uniform vec4 fire_cool : source_color = vec4(0.9, 0.22, 0.04, 1.0);
uniform vec4 scorch : source_color = vec4(0.2, 0.08, 0.02, 1.0);

float hash(vec2 p) {
	return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec2 p) {
	vec2 i = floor(p);
	vec2 f = fract(p);
	vec2 u = f * f * (3.0 - 2.0 * f);
	return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0)), u.x), u.y);
}

float fbm(vec2 p) {
	float value = 0.0;
	float amplitude = 0.5;
	for (int i = 0; i < 5; i++) {
		value += amplitude * noise(p);
		p *= 2.03;
		amplitude *= 0.5;
	}
	return value;
}

// Bright dots drifting up through a grid of [cells], one in about every eighth cell.
float sparks(vec2 p, float cells, float speed) {
	vec2 q = p * cells + vec2(0.0, TIME * speed);
	vec2 cell = floor(q);
	float h = hash(cell);
	vec2 at = vec2(hash(cell + 3.1), hash(cell + 7.7)) * 0.6 + 0.2;
	float dot_size = 0.08 + 0.06 * hash(cell + 1.3);
	float lit = step(0.88, h) * (0.6 + 0.4 * sin(TIME * 9.0 + h * 50.0));
	return lit * (1.0 - smoothstep(0.0, dot_size, length(fract(q) - at)));
}

void fragment() {
	vec2 aspect = vec2(SCREEN_PIXEL_SIZE.y / SCREEN_PIXEL_SIZE.x, 1.0);
	vec2 p = UV * aspect;

	// 0 at the screen's edge, 1 at its centre, roughened so the front licks inward unevenly.
	vec2 to_edge = min(UV, 1.0 - UV) * 2.0;
	// A soft minimum, so the corners burn round instead of along a diagonal seam.
	float inward = -log(exp(-to_edge.x * 6.0) + exp(-to_edge.y * 6.0)) / 6.0 + 0.1155;
	float ragged = fbm(p * 3.0 + vec2(0.0, TIME * 0.12)) - 0.5;
	float flicker = noise(p * 16.0 + vec2(0.0, TIME * 3.0));
	float front = inward + ragged * 0.45 + (flicker - 0.5) * 0.03;

	// Past the centre at 1, so the last corner burns too.
	float reach = mix(-0.3, 1.45, progress);
	float behind = reach - front;

	float scorched = smoothstep(-band * 3.0, 0.0, behind);
	float charred = smoothstep(0.0, band, behind);
	float flame = exp(-pow(behind / (band * 0.45), 2.0));

	vec3 ground = mix(scorch.rgb, vec3(0.0), charred);
	vec3 fire = mix(fire_cool.rgb, fire_hot.rgb, flame * flicker) * (1.3 + flicker);
	vec3 colour = mix(ground, fire, flame);
	float alpha = max(max(scorched * 0.55, charred), flame);

	// Embers glow on in the char just behind the front, and sparks rise from the fire.
	float near_front = 1.0 - smoothstep(band * 0.5, band * 2.0, behind);
	float embers = step(0.975, hash(floor(p * 140.0))) * charred * near_front;
	embers *= 0.55 + 0.45 * sin(TIME * 5.0 + hash(floor(p * 140.0)) * 40.0);
	float in_fire = 1.0 - smoothstep(band * 0.6, band * 1.6, abs(behind + band * 0.3));
	float rising = sparks(p, 22.0, 2.2) * in_fire;
	colour += fire_hot.rgb * (embers + rising) * 1.6;
	alpha = max(alpha, rising);

	COLOR = vec4(colour, clamp(alpha, 0.0, 1.0));
}
