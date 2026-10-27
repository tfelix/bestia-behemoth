shader_type sky;

// The whole sky: a painted gradient, the sun, the moon, stars, and a haze band that meets the fog.
//
// environment.gd sets every uniform here from the clock and the weather. Nothing is read from the lights,
// because the sun light stops at a floor above the horizon to keep shadows short, and the disc must not.

group_uniforms palette;
uniform vec3 zenith_colour : source_color = vec3(0.20, 0.52, 0.92);
uniform vec3 horizon_colour : source_color = vec3(0.60, 0.84, 0.97);
uniform vec3 ground_colour : source_color = vec3(0.32, 0.36, 0.34);

// How fast the zenith colour takes over above the horizon. Below 1 the blue reaches further down.
uniform float gradient_curve : hint_range(0.1, 2.0) = 0.55;

// The fog's own colour. The sky fades into it at the horizon, so terrain fading into fog meets sky of the same
// colour instead of a seam.
uniform vec3 haze_colour : source_color = vec3(0.63, 0.73, 0.83);

// How high the haze band reaches, as the sine of an elevation. Weather raises it as visibility drops.
uniform float haze_height : hint_range(0.0, 1.0) = 0.08;
group_uniforms;

group_uniforms sun;
// Points toward the sun. May be below the horizon.
uniform vec3 sun_direction = vec3(0.0, 0.7, -0.7);
uniform vec3 sun_colour : source_color = vec3(1.0, 0.96, 0.88);

// The disc's angular radius, in degrees. Far larger than the real sun's quarter degree: a true-size sun is a
// few pixels across from the game camera.
uniform float sun_size_degrees : hint_range(0.1, 10.0) = 2.2;

// How much brighter than white the disc is. Above 1 it stays white after clouds and haze take their share.
uniform float sun_disc_energy : hint_range(0.0, 20.0) = 6.0;

// The warm glow around the sun, and how tight it is.
uniform vec3 glow_colour : source_color = vec3(1.0, 0.75, 0.45);
uniform float glow_strength : hint_range(0.0, 4.0) = 0.6;
uniform float glow_tightness : hint_range(1.0, 64.0) = 6.0;
group_uniforms;

group_uniforms moon;
// Points toward the moon. May be below the horizon.
uniform vec3 moon_direction = vec3(0.0, 0.5, 0.86);

// 0 new, 0.5 full, 1 new again. Waxing and waning light opposite sides of the disc.
uniform float moon_phase : hint_range(0.0, 1.0) = 0.5;
uniform vec3 moon_colour : source_color = vec3(0.92, 0.94, 1.0);
uniform float moon_size_degrees : hint_range(0.1, 10.0) = 2.6;

// How bright the disc is drawn. Lower by day, where a moon is a pale shape rather than a light.
uniform float moon_energy : hint_range(0.0, 8.0) = 1.6;

// The faint light on the moon's dark side, so a crescent still shows its whole disc.
uniform float earthshine : hint_range(0.0, 0.3) = 0.03;
group_uniforms;

group_uniforms stars;
// 0 by day, 1 in full night.
uniform float star_visibility : hint_range(0.0, 1.0) = 0.0;

// The star field turns once a day around a tilted pole, so the night sky moves with the hour.
uniform float star_rotation = 0.0;
uniform float star_density : hint_range(20.0, 400.0) = 110.0;
group_uniforms;

float sun_disc(vec3 eye) {
	float radius = radians(sun_size_degrees);
	float cos_angle = dot(eye, sun_direction);

	// The soft edge is a fixed fraction of the radius, so the disc stays round at any size.
	return smoothstep(cos(radius), cos(radius * 0.85), cos_angle);
}

float hash13(vec3 p) {
	p = fract(p * 0.1031);
	p += dot(p, p.zyx + 31.32);
	return fract((p.x + p.y) * p.z);
}

vec3 hash33(vec3 p) {
	p = fract(p * vec3(0.1031, 0.1030, 0.0973));
	p += dot(p, p.yxz + 33.33);
	return fract((p.xxy + p.yxx) * p.zyx);
}

float value_noise(vec2 p) {
	vec2 cell = floor(p);
	vec2 f = fract(p);
	f = f * f * (3.0 - 2.0 * f);

	float a = hash13(vec3(cell, 0.0));
	float b = hash13(vec3(cell + vec2(1.0, 0.0), 0.0));
	float c = hash13(vec3(cell + vec2(0.0, 1.0), 0.0));
	float d = hash13(vec3(cell + vec2(1.0, 1.0), 0.0));

	return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

// The moon's colour and coverage where the eye ray meets its disc; alpha 0 off the disc.
vec4 moon_disc(vec3 eye) {
	float radius = tan(radians(moon_size_degrees));

	// A frame on the disc: "right" points at the sun's side, so the lit edge always faces the sun.
	vec3 forward = moon_direction;
	vec3 toward_sun = sun_direction - forward * dot(sun_direction, forward);
	vec3 right = length(toward_sun) > 0.001 ? normalize(toward_sun) : normalize(cross(forward, vec3(0.0, 1.0, 0.0)));
	vec3 up = cross(right, forward);

	float along = dot(eye, forward);
	if (along <= 0.0) {
		return vec4(0.0);
	}

	vec2 disc = vec2(dot(eye, right), dot(eye, up)) / along / radius;
	float r2 = dot(disc, disc);
	float cover = 1.0 - smoothstep(0.85, 1.0, sqrt(r2));
	if (cover <= 0.0) {
		return vec4(0.0);
	}

	// The sphere's normal at this point, with z toward the viewer. The sun lights it from the side by phase:
	// from behind at new moon, from in front at full moon. Taking the sine's magnitude keeps the light on the
	// sun's side in both halves of the cycle.
	vec3 normal = vec3(disc, sqrt(max(1.0 - r2, 0.0)));
	float angle = 6.28318 * moon_phase;
	vec3 light = vec3(abs(sin(angle)), 0.0, -cos(angle));
	float lit = smoothstep(-0.05, 0.12, dot(normal, light));

	// Painted maria: a few soft darker patches, fixed to the disc.
	float maria = smoothstep(0.45, 0.75, value_noise(disc * 2.2 + 7.0));
	vec3 surface = moon_colour * (1.0 - 0.28 * maria);

	return vec4(surface * max(lit, earthshine), cover);
}

// The soft light around the moon, as wide as its lit part is bright.
vec3 moon_halo(vec3 eye) {
	float toward = max(dot(eye, moon_direction), 0.0);
	float lit = (1.0 - cos(6.28318 * moon_phase)) * 0.5;

	return moon_colour * lit * moon_energy * (0.25 * pow(toward, 900.0) + 0.06 * pow(toward, 60.0))
		* step(0.0, eye.y);
}

vec3 stars(vec3 eye) {
	// Turned about a pole tilted toward the north, so stars wheel across the sky rather than spin on the zenith.
	float c = cos(star_rotation);
	float s = sin(star_rotation);
	vec3 pole = normalize(vec3(0.0, 0.8, -0.6));
	vec3 turned = eye * c + cross(pole, eye) * s + pole * dot(pole, eye) * (1.0 - c);

	vec3 p = turned * star_density;
	vec3 cell = floor(p);
	vec3 centre = cell + 0.2 + 0.6 * hash33(cell);

	float chance = hash13(cell + 17.0);
	if (chance > 0.18) {
		return vec3(0.0);
	}

	// No smaller than a pixel or so at the game's resolution, or most stars flicker in and out as they turn.
	float size = mix(0.12, 0.26, hash13(cell + 3.0));
	float point = 1.0 - smoothstep(0.0, size, length(p - centre));
	float twinkle = 0.75 + 0.25 * sin(TIME * (1.5 + 3.0 * chance) + chance * 40.0);

	// A few warm and a few blue stars; most are white.
	vec3 tint = mix(vec3(1.0, 0.85, 0.7), vec3(0.75, 0.85, 1.0), hash13(cell + 9.0));

	return tint * point * twinkle * (0.5 + 6.0 * (0.18 - chance));
}

vec3 gradient(vec3 eye) {
	if (eye.y < 0.0) {
		// Below the horizon the sky stands in for ground past the fog's end, so it is fog colour until well
		// below the horizon. A ground colour right under it shows as a band between the fogged land and the sky.
		return mix(haze_colour, ground_colour, smoothstep(0.1, 0.5, -eye.y));
	}

	return mix(horizon_colour, zenith_colour, pow(eye.y, gradient_curve));
}

vec3 sun_glow(vec3 eye) {
	float toward = max(dot(eye, sun_direction), 0.0);

	// The glow sits on the horizon when the sun does. That is what reads as a sunset rather than a lamp.
	float low_sun = 1.0 - smoothstep(0.0, 0.5, sun_direction.y);
	float horizon_band = 1.0 - smoothstep(0.0, 0.35, abs(eye.y));
	float spread = glow_strength * (pow(toward, glow_tightness) + 0.5 * low_sun * horizon_band * pow(toward, 2.0));

	// Fades out once the sun is well below the horizon; twilight colours are in the gradient by then.
	return glow_colour * spread * smoothstep(-0.25, 0.0, sun_direction.y);
}

void sky() {
	vec3 eye = normalize(EYEDIR);

	vec3 colour = gradient(eye) + sun_glow(eye);

	vec4 moon = moon_disc(eye) * step(0.0, eye.y);

	// Stars stay out of the radiance pass: they light nothing, and as specks they would only add noise.
	if (!AT_CUBEMAP_PASS && star_visibility > 0.0 && eye.y > 0.0) {
		colour += stars(eye) * star_visibility * (1.0 - moon.a);
	}

	// Added rather than painted over, so the unlit side of a day moon lets the blue through instead of
	// showing as a dark hole in it.
	colour += moon.rgb * moon_energy * moon.a;
	colour += moon_halo(eye);

	float haze = 1.0 - smoothstep(0.0, max(haze_height, 0.001), abs(eye.y));
	colour = mix(colour, haze_colour, haze);

	// Skipped in the radiance pass: a pinpoint that bright turns every glossy surface into a mirror of it.
	if (!AT_CUBEMAP_PASS && eye.y > -0.01) {
		// Only partly behind the haze, or the sun would vanish just before it sets.
		colour += sun_colour * sun_disc_energy * sun_disc(eye) * (1.0 - 0.6 * haze);
	}

	COLOR = colour;
}
