// Smoke rising off burning ground and drifting downwind. CUSTOM.y is the particle's age, 0 to 1.

shader_type particles;

#include "res://Game/VFX/GroundFire/ground_fire_particles.gdshaderinc"

uniform vec4 colour : source_color = vec4(0.42, 0.4, 0.38, 0.4);
uniform float rise_speed : hint_range(0.0, 5.0) = 2.2;

// The share of the wind's own speed the smoke is carried at, once it has caught up with it.
uniform float wind_follow : hint_range(0.0, 1.0) = 0.6;

uniform float start_size : hint_range(0.05, 4.0) = 0.7;
uniform float end_size : hint_range(0.05, 8.0) = 2.8;

void start() {
	uint seed = ground_fire_hash(NUMBER * 7919u + RANDOM_SEED);

	if (RESTART_POSITION) {
		vec3 point = ground_fire_emission_point(seed) + vec3(0.0, 0.4, 0.0);
		TRANSFORM = EMISSION_TRANSFORM * mat4(
			vec4(1.0, 0.0, 0.0, 0.0), vec4(0.0, 1.0, 0.0, 0.0), vec4(0.0, 0.0, 1.0, 0.0), vec4(point, 1.0));
	}

	if (RESTART_VELOCITY) {
		VELOCITY = vec3(
			ground_fire_random(seed) - 0.5,
			rise_speed * mix(0.7, 1.3, ground_fire_random(seed)),
			ground_fire_random(seed) - 0.5) * vec3(0.4, 1.0, 0.4);
	}

	if (RESTART_CUSTOM) {
		// w shortens some lives, so the plume does not end in one flat layer.
		CUSTOM = vec4(0.0, 0.0, ground_fire_random(seed), mix(0.6, 1.0, ground_fire_random(seed)));
	}

	ACTIVE = emission_point_count > 0;
}

void process() {
	CUSTOM.y += DELTA / max(LIFETIME * CUSTOM.w, 0.001);
	float age = CUSTOM.y;
	if (age >= 1.0) {
		ACTIVE = false;
	}

	vec2 downwind = weather_wind.xz * wind_follow;
	VELOCITY.xz = mix(VELOCITY.xz, downwind, 1.0 - exp(-DELTA * 1.2));
	VELOCITY.y *= exp(-DELTA * 0.2);

	TRANSFORM = ground_fire_scaled(TRANSFORM, mix(start_size, end_size, sqrt(age)));

	float alpha = smoothstep(0.0, 0.12, age) * (1.0 - smoothstep(0.4, 1.0, age));
	COLOR = vec4(colour.rgb, colour.a * alpha);
}
