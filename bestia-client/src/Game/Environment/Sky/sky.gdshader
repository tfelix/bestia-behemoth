shader_type sky;

// The whole sky: a painted gradient, the sun, and a haze band that meets the fog.
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

float sun_disc(vec3 eye) {
	float radius = radians(sun_size_degrees);
	float cos_angle = dot(eye, sun_direction);

	// The soft edge is a fixed fraction of the radius, so the disc stays round at any size.
	return smoothstep(cos(radius), cos(radius * 0.85), cos_angle);
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

	float haze = 1.0 - smoothstep(0.0, max(haze_height, 0.001), abs(eye.y));
	colour = mix(colour, haze_colour, haze);

	// Skipped in the radiance pass: a pinpoint that bright turns every glossy surface into a mirror of it.
	if (!AT_CUBEMAP_PASS && eye.y > -0.01) {
		// Only partly behind the haze, or the sun would vanish just before it sets.
		colour += sun_colour * sun_disc_energy * sun_disc(eye) * (1.0 - 0.6 * haze);
	}

	COLOR = colour;
}
