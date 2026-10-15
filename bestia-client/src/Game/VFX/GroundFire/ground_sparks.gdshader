// Sparks thrown up by burning ground: fast, small and short-lived, cooling from yellow to red.
// CUSTOM.y is the particle's age, 0 to 1.

shader_type particles;

#include "res://Game/VFX/GroundFire/ground_fire_particles.gdshaderinc"

uniform vec4 hot_colour : source_color = vec4(1.0, 0.85, 0.4, 1.0);
uniform vec4 cool_colour : source_color = vec4(0.9, 0.2, 0.03, 0.0);
uniform float rise_speed : hint_range(0.0, 10.0) = 3.0;
uniform float wind_follow : hint_range(0.0, 1.0) = 0.8;
uniform float size : hint_range(0.01, 0.5) = 0.1;

// How hard a spark is buffeted sideways, in m/s².
uniform float flutter : hint_range(0.0, 10.0) = 3.0;

void start() {
	uint seed = ground_fire_hash(NUMBER * 104729u + RANDOM_SEED);

	if (RESTART_POSITION) {
		vec3 point = ground_fire_emission_point(seed) + vec3(0.0, 0.3, 0.0);
		TRANSFORM = EMISSION_TRANSFORM * mat4(
			vec4(1.0, 0.0, 0.0, 0.0), vec4(0.0, 1.0, 0.0, 0.0), vec4(0.0, 0.0, 1.0, 0.0), vec4(point, 1.0));
	}

	if (RESTART_VELOCITY) {
		VELOCITY = vec3(
			(ground_fire_random(seed) - 0.5) * 1.2,
			rise_speed * mix(0.6, 1.4, ground_fire_random(seed)),
			(ground_fire_random(seed) - 0.5) * 1.2);
	}

	if (RESTART_CUSTOM) {
		CUSTOM = vec4(0.0, 0.0, ground_fire_random(seed), mix(0.5, 1.0, ground_fire_random(seed)));
	}

	ACTIVE = emission_point_count > 0;
}

void process() {
	CUSTOM.y += DELTA / max(LIFETIME * CUSTOM.w, 0.001);
	float age = CUSTOM.y;
	if (age >= 1.0) {
		ACTIVE = false;
	}

	float phase = CUSTOM.z * 40.0;
	VELOCITY.xz += vec2(sin(TIME * 7.0 + phase), cos(TIME * 6.1 + phase * 0.7)) * flutter * DELTA;
	VELOCITY.xz = mix(VELOCITY.xz, weather_wind.xz * wind_follow, 1.0 - exp(-DELTA * 1.5));
	VELOCITY.y -= 1.5 * DELTA;

	TRANSFORM = ground_fire_scaled(TRANSFORM, size * (1.0 - 0.5 * age));
	COLOR = mix(hot_colour, cool_colour, age);
}
