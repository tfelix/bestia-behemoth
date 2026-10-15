// Shared by the smoke and spark process shaders that `GroundSmoke` runs on each burning column.

// Where the column's burning cells are, one RGB float texel per cell, relative to the particle node.
uniform sampler2D emission_points : filter_nearest;
uniform int emission_point_count = 0;

global uniform vec3 weather_wind;

uint ground_fire_hash(uint x) {
	x ^= x >> 16u;
	x *= 0x7feb352du;
	x ^= x >> 15u;
	x *= 0x846ca68bu;
	x ^= x >> 16u;
	return x;
}

float ground_fire_random(inout uint seed) {
	seed = ground_fire_hash(seed);
	return float(seed) / 4294967295.0;
}

// A random burning cell, at a random spot inside its square metre.
vec3 ground_fire_emission_point(inout uint seed) {
	int last = max(emission_point_count - 1, 0);
	int index = clamp(int(ground_fire_random(seed) * float(emission_point_count)), 0, last);
	vec3 point = texelFetch(emission_points, ivec2(index, 0), 0).xyz;
	point.xz += vec2(ground_fire_random(seed), ground_fire_random(seed)) - 0.5;
	return point;
}

mat4 ground_fire_scaled(mat4 transform, float size) {
	transform[0].xyz = vec3(size, 0.0, 0.0);
	transform[1].xyz = vec3(0.0, size, 0.0);
	transform[2].xyz = vec3(0.0, 0.0, size);
	return transform;
}
