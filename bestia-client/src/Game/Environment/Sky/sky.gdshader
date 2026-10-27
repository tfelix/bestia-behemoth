shader_type sky;

// The whole sky: a painted gradient, the sun, the moon, stars, painted clouds, and a haze band that meets the fog.
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

group_uniforms clouds;
// One seamless fbm noise tile. Every cloud octave samples it at a different scale.
uniform sampler2D cloud_noise : repeat_enable, filter_linear_mipmap;

// Round puffs: inverted cellular noise. Added to the fbm it bulges every cloud edge into the heaped outline of
// a cumulus, where fbm alone gives ragged flat patches.
uniform sampler2D cloud_puffs : repeat_enable, filter_linear_mipmap;

// How much of the sky is cloud, 0 clear to 1 closed deck.
uniform float cloud_coverage : hint_range(0.0, 1.0) = 0.4;

// How heavy the clouds are: denser cores and darker undersides. Storm clouds sit near 1.
uniform float cloud_thickness : hint_range(0.0, 1.0) = 0.2;

uniform vec3 cloud_lit_colour : source_color = vec3(1.0, 0.99, 0.96);
uniform vec3 cloud_shadow_colour : source_color = vec3(0.60, 0.70, 0.84);

// Points toward whatever lights the clouds: the sun by day, the moon by night.
uniform vec3 cloud_light_direction = vec3(0.0, 0.7, -0.7);

// How far the wind has carried the clouds, in metres on the cloud deck.
uniform vec2 cloud_offset = vec2(0.0);

// The deck is a flat plane this high up; it bends down toward the horizon so far clouds do not shrink to
// nothing.
uniform float cloud_altitude = 220.0;

// How far the deck bends down toward the horizon. Small keeps it flat, so clouds near the horizon flatten to
// slivers in the haze; the game camera only ever sees the lowest twenty degrees of sky, so it needs clouds
// that still stand up there.
uniform float deck_curve : hint_range(0.02, 1.0) = 0.35;

// Elevation, as a sine, over which clouds fade into the haze at the horizon.
uniform float cloud_horizon_fade : hint_range(0.0, 0.3) = 0.03;

// Metres across one noise tile. Larger means fewer, bigger clouds.
uniform float cloud_tile_metres = 1800.0;

// How many flat light bands a cloud is painted in. The painted look is in these steps.
uniform float cloud_bands : hint_range(1.0, 8.0) = 3.0;

// High wisps along the wind, faint. Cleared by a closed deck, which hides them.
uniform float cirrus_amount : hint_range(0.0, 1.0) = 0.5;

// Lightning lighting the clouds from inside, 0 to 1.
uniform float flash : hint_range(0.0, 1.0) = 0.0;
uniform vec3 flash_colour : source_color = vec3(0.86, 0.90, 1.0);
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

// Where the eye ray meets the cloud deck, in metres. The divisor is lifted so the deck curves away like the
// ground does, rather than running out to infinity at the horizon.
vec2 deck_point(vec3 eye) {
	return eye.xz / (eye.y + deck_curve) * cloud_altitude;
}

// The large shapes only: where clouds are and the big puffs they are heaped from. Shading reads this, so a
// cloud is shaded as a few big masses rather than spotted by every small puff.
float cloud_mass(vec2 uv) {
	return texture(cloud_noise, uv).r * 0.66 + texture(cloud_puffs, uv * 2.3 + 0.37).r * 0.34;
}

float cloud_density(vec2 metres) {
	vec2 uv = (metres - cloud_offset) / cloud_tile_metres;

	// The small puffs only shape the outline: two sizes of them, so the edge is heaped from lobes on lobes the way
	// a cumulus is, rather than one row of equal bumps.
	return cloud_mass(uv) * 0.82 + texture(cloud_puffs, uv * 6.1 + 0.71).r * 0.12
		+ texture(cloud_puffs, uv * 14.3 + 0.29).r * 0.06;
}

// How much of the deck at this density counts as cloud. Coverage lowers the threshold, thickness sharpens
// and fills the cores.
float cloud_cover_at(float density) {
	// Curved so that the reported cover reads as roughly that share of the sky.
	float threshold = mix(0.72, 0.2, pow(cloud_coverage, 2.0));

	// A painted cloud has a crisp outline. The noise is magnified many times over a cloud near the zenith, so a
	// softness in density units smeared that outline over a fifth of the screen; a pixel or two does not.
	float softness = max(fwidth(density) * 1.5, 0.004) + 0.02 * cloud_thickness;
	float cover = smoothstep(threshold - softness, threshold + softness, density);

	// The last stretch toward a closed deck fills the gaps outright. Noise alone never quite closes, and an
	// overcast sky with blue holes in it does not read as overcast.
	return max(cover, smoothstep(0.85, 1.0, cloud_coverage));
}

// The cloud's colour and coverage along one eye ray; alpha 0 where there is no cloud.
vec4 cumulus(vec3 eye, bool painted) {
	if (eye.y <= 0.0 || cloud_coverage <= 0.001) {
		return vec4(0.0);
	}

	vec2 here = deck_point(eye);
	float density = cloud_density(here);
	float cover = cloud_cover_at(density);
	if (cover <= 0.001) {
		return vec4(0.0);
	}

	// Lit where the cloud thins toward the light and shaded where it thickens: the slope of the density
	// toward the light, not how much cloud there is. So each puff gets a bright side and a shaded side, and
	// the inside of a big cloud stays bright instead of all falling into shadow.
	// Sampled a big puff away, not a small one: a short step finds every dip inside a cloud and paints it as a
	// shaded hollow, the dark blue "eyes" that made a deck read as camouflage rather than as cumulus.
	vec2 toward_light = normalize(cloud_light_direction.xz + vec2(0.0001));
	vec2 uv = (here - cloud_offset) / cloud_tile_metres;
	float mass = cloud_mass(uv);
	float slope = mass - cloud_mass(uv + toward_light * 0.04);
	float blocked = clamp(-slope * 4.0, 0.0, 1.0);
	float light = clamp(0.95 + slope * 3.0, 0.45, 1.0);

	// The side of a cloud nearest the viewer is its base, seen from below, and the base is in its own shade.
	// That is what gives a painted cumulus a bright heaped top over a flat grey-blue underside.
	vec2 outward = normalize(eye.xz + vec2(0.0001));
	float base = clamp((mass - cloud_mass(uv - outward * 0.03)) * 6.0, 0.0, 1.0);
	// Straight overhead "nearest the viewer" has no direction, so the base shading fades out toward the zenith.
	light *= 1.0 - 0.45 * base * (1.0 - smoothstep(0.6, 0.95, eye.y));

	// Thick cloud is darker underneath, which is most of what the eye sees from below. In a storm that is
	// what breaks a closed deck into dark heavy masses instead of a flat grey sheet.
	light *= 1.0 - cloud_thickness * smoothstep(0.3, 0.7, mass) * 0.45;

	// Painted steps: flat bands with a narrow blend between them, rather than a smooth ramp.
	if (painted) {
		float scaled = light * cloud_bands;
		float band = floor(scaled);
		light = (band + smoothstep(0.2, 0.8, fract(scaled))) / cloud_bands;
	}

	// Heavy cloud is grey even where it is lit; that, more than the cover, is what makes a storm sky.
	vec3 lit = cloud_lit_colour * (1.0 - 0.45 * cloud_thickness);
	vec3 shaded = cloud_shadow_colour * (1.0 - 0.7 * cloud_thickness);
	vec3 colour = mix(shaded, lit, light);

	// The thin edge toward the light glows, the silver lining every painted cumulus has. Only on that side:
	// a glow all round reads as an outline drawn around a cut-out.
	float edge = cover * (1.0 - cover) * 4.0;
	float facing = pow(max(dot(eye, cloud_light_direction), 0.0), 3.0);
	colour += lit * edge * facing * (1.0 - blocked) * 0.6;

	colour += flash_colour * flash * (0.6 + 0.8 * cover);

	// Far clouds sink into the haze rather than meeting the horizon as a hard band.
	float opacity = cover * smoothstep(0.0, cloud_horizon_fade, eye.y);

	return vec4(colour, opacity);
}

// Long thin streaks high above the cumulus, stretched along the wind.
vec4 cirrus(vec3 eye) {
	if (eye.y <= 0.0 || cirrus_amount <= 0.001) {
		return vec4(0.0);
	}

	vec2 wind = normalize(cloud_offset + vec2(0.0001, 0.0));
	vec2 across = vec2(-wind.y, wind.x);

	// Higher than the cumulus, so it moves slower across the sky for the same wind.
	vec2 metres = eye.xz / (eye.y + 0.2) * cloud_altitude * 3.0 - cloud_offset * 0.6;
	vec2 streak = vec2(dot(metres, wind) * 0.3, dot(metres, across)) / cloud_tile_metres;

	// Broad patches of streaks rather than single long needles: the patch decides where, the stretched
	// octave draws soft brush strokes inside it.
	float patch = texture(cloud_noise, streak * 0.8 + 0.13).r;
	float strokes = texture(cloud_noise, streak * vec2(1.2, 4.0) + 0.53).r;
	float amount = smoothstep(0.5, 0.75, patch) * smoothstep(0.4, 0.7, strokes);

	return vec4(cloud_lit_colour, amount * cirrus_amount * 0.35 * smoothstep(0.0, 0.25, eye.y));
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

	// Steps and wisps are left out of the radiance pass: ambient light needs to know how much cloud there is
	// and what colour it is lit, not how it is painted.
	vec4 high = AT_CUBEMAP_PASS ? vec4(0.0) : cirrus(eye);
	vec4 cloud = cumulus(eye, !AT_CUBEMAP_PASS);

	vec4 moon = moon_disc(eye) * step(0.0, eye.y);

	// Stars stay out of the radiance pass: they light nothing, and as specks they would only add noise.
	if (!AT_CUBEMAP_PASS && star_visibility > 0.0 && eye.y > 0.0) {
		colour += stars(eye) * star_visibility * (1.0 - moon.a) * (1.0 - high.a) * (1.0 - cloud.a);
	}

	// Added rather than painted over, so the unlit side of a day moon lets the blue through instead of
	// showing as a dark hole in it.
	colour += moon.rgb * moon_energy * moon.a;
	colour += moon_halo(eye);

	colour = mix(colour, high.rgb, high.a);
	colour = mix(colour, cloud.rgb, cloud.a);

	float haze = 1.0 - smoothstep(0.0, max(haze_height, 0.001), abs(eye.y));
	colour = mix(colour, haze_colour, haze);

	// Skipped in the radiance pass: a pinpoint that bright turns every glossy surface into a mirror of it.
	if (!AT_CUBEMAP_PASS && eye.y > -0.01) {
		// Only partly behind the haze, or the sun would vanish just before it sets.
		colour += sun_colour * sun_disc_energy * sun_disc(eye) * (1.0 - 0.6 * haze) * (1.0 - cloud.a);
	}

	COLOR = colour;
}
