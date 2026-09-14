shader_type spatial;

// Two-sided because there is no underside to draw: the ground/water interface belongs to the terrain pass, so a
// swimming player looking up sees the back of this one sheet and nothing else. `depth_draw_never` because the
// whole shader is built on reading what stands behind the water, and a surface that writes depth cannot see past
// itself.
render_mode blend_mix, depth_draw_never, cull_disabled, diffuse_burley, specular_schlick_ggx;

// Declared rather than built in, which is the Godot 4 spelling. This is the only reason the shoreline works: the
// distance from this fragment to whatever opaque thing is behind it is the depth of water the view ray crosses,
// and every term below is a function of it.
// `filter_nearest` and no repeat, because a depth value is an index into a scene and not a colour: interpolating
// two of them across a silhouette invents a surface half way between the near object and the far one, which reads
// as a halo of the wrong depth around everything the water stands behind.
uniform sampler2D depth_texture : hint_depth_texture, repeat_disable, filter_nearest;

group_uniforms Colour;

// Shallow water is nearly clear, so the bed shows through and the waterline stops being an edge. Deep water is
// what is left once the light has been absorbed on the way down and back.
uniform vec3 shallow_colour : source_color = vec3(0.32, 0.61, 0.62);
uniform vec3 deep_colour : source_color = vec3(0.04, 0.13, 0.22);

// Metres of water the view ray crosses before the colour is most of the way to `deep_colour`. Absorption is
// exponential, so this is a scale length rather than a limit.
uniform float absorption_distance : hint_range(0.5, 40.0) = 7.0;

// The same thing for opacity, and shorter: water hides the bed sooner than it finishes changing colour.
uniform float opacity_distance : hint_range(0.2, 20.0) = 3.5;

// What the deepest water is allowed to reach. Below one, because a sea that goes fully opaque loses the
// difference between a shelf and a trench.
uniform float max_opacity : hint_range(0.1, 1.0) = 0.88;

group_uniforms;

group_uniforms Shore;

// How far up the beach the foam reaches, measured in the same view-ray metres as everything else.
uniform float foam_distance : hint_range(0.05, 4.0) = 0.8;

uniform vec3 foam_colour : source_color = vec3(0.92, 0.95, 0.96);

// Foam supplies its own opacity, and it has to: the water's own alpha is heading for zero at exactly the place
// the foam is, so a foam term that only tinted the albedo would fade out with the surface carrying it.
uniform float foam_opacity : hint_range(0.0, 1.0) = 0.75;

group_uniforms;

group_uniforms Waves;

// A seamless noise texture supplied by water.tres, not an imported asset - the grass canopy does the same.
uniform sampler2D wave_noise : repeat_enable, filter_linear_mipmap;

// Metres per tile of the noise. Two layers sample it at this scale and at a fraction of it, so the surface has a
// swell and a chop rather than one frequency repeating visibly.
uniform float wave_scale : hint_range(2.0, 200.0) = 26.0;

uniform float wave_speed : hint_range(0.0, 1.0) = 0.045;

// How far the normal is allowed to tilt. Normals only - the sheet itself stays where the mesher put it, because
// the server's water height is what decides wading and swimming and a displaced surface would disagree with it.
uniform float wave_strength : hint_range(0.0, 1.0) = 0.28;

// What rain adds on top. Real rain stipples a water surface, and the wetness global is already what every other
// material reads, so the sea reacts to weather without a second source of truth.
uniform float rain_chop : hint_range(0.0, 1.0) = 0.45;

group_uniforms;

group_uniforms Sheen;

// Water is smooth, and this is the only smooth thing in the scene.
uniform float water_roughness : hint_range(0.0, 0.5) = 0.06;

// Deliberately modest. A mirror-smooth sheet reflecting the placeholder procedural sky turns the sea white and
// hides whether anything else is right - the same trap the terrain material's own comment records for its
// specular. Raise it once there is a real sky to reflect.
uniform float sheen_strength : hint_range(0.0, 1.0) = 0.35;

uniform float fresnel_power : hint_range(1.0, 8.0) = 4.0;

group_uniforms;

// Driven by WeatherState, as globals so water, foliage and terrain read the same numbers.
global uniform float weather_wetness;

varying vec3 v_world_pos;

void vertex() {
	v_world_pos = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xyz;
}

// The wave height field: two layers of the same noise at different scales, drifting in different directions so
// neither the tiling nor the motion reads as one repeating thing.
float wave_height(vec2 world_xz, float time, float chop) {
	vec2 swell_uv = world_xz / wave_scale + vec2(time * wave_speed, time * wave_speed * 0.6);
	vec2 chop_uv = world_xz / (wave_scale * 0.27) + vec2(-time * wave_speed * 1.7, time * wave_speed * 1.1);

	return texture(wave_noise, swell_uv).r + chop * texture(wave_noise, chop_uv).r;
}

void fragment() {
	// How far the view ray travels through water before it hits something opaque. The bed is meshed now, so on a
	// shore there is something to hit; where nothing has streamed in yet the depth buffer holds the far plane and
	// the water simply reads as deep, which is the safe way for this to be wrong.
	float raw_depth = texture(depth_texture, SCREEN_UV).x;
	vec3 ndc = vec3(SCREEN_UV * 2.0 - 1.0, raw_depth);
	vec4 behind = INV_PROJECTION_MATRIX * vec4(ndc, 1.0);
	behind.xyz /= behind.w;

	float water_depth = max(0.0, -behind.z - (-VERTEX.z));

	float chop = mix(0.35, 0.35 + rain_chop, clamp(weather_wetness, 0.0, 1.0));
	float texel = wave_scale * 0.02;

	// Central differences on the height field. Cheaper than a normal map and it needs no tangents, which this
	// mesh has none of - surface nets emits none.
	float left = wave_height(v_world_pos.xz - vec2(texel, 0.0), TIME, chop);
	float right = wave_height(v_world_pos.xz + vec2(texel, 0.0), TIME, chop);
	float back = wave_height(v_world_pos.xz - vec2(0.0, texel), TIME, chop);
	float front = wave_height(v_world_pos.xz + vec2(0.0, texel), TIME, chop);

	vec3 world_normal = normalize(vec3(
		(left - right) * wave_strength,
		1.0,
		(back - front) * wave_strength
	));

	// A back face is the underside seen by a submerged camera, and its normal points the other way.
	if (!FRONT_FACING) {
		world_normal = -world_normal;
	}

	// Absorption, as two scale lengths rather than one: the colour settles more slowly than the opacity, which is
	// what keeps a shallow shelf readable as green rather than as thin blue.
	float tint = 1.0 - exp(-water_depth / absorption_distance);
	float body = 1.0 - exp(-water_depth / opacity_distance);

	vec3 albedo = mix(shallow_colour, deep_colour, tint);
	float alpha = body * max_opacity;

	// Grazing angles hide the bed whatever the depth, which is what stops a wide sea fading to nothing at the
	// horizon once the shoreline term has made the near water clear.
	vec3 view_direction = normalize(-VERTEX);
	vec3 view_normal = normalize((VIEW_MATRIX * vec4(world_normal, 0.0)).xyz);
	float fresnel = pow(1.0 - clamp(dot(view_normal, view_direction), 0.0, 1.0), fresnel_power);

	alpha = clamp(alpha + fresnel * (1.0 - alpha) * sheen_strength, 0.0, 1.0);

	// Foam, broken up by the same noise the waves use so the line it draws is ragged rather than a contour of the
	// depth it is computed from.
	float shore = smoothstep(foam_distance, 0.0, water_depth);
	float crest = texture(wave_noise, v_world_pos.xz / (wave_scale * 0.18) + vec2(TIME * wave_speed * 2.0, 0.0)).r;
	float foam = shore * smoothstep(0.25, 0.75, crest * 0.6 + shore * 0.5);

	albedo = mix(albedo, foam_colour, foam);
	alpha = max(alpha, foam * foam_opacity);

	ALBEDO = albedo;
	ALPHA = alpha;
	NORMAL = view_normal;
	ROUGHNESS = water_roughness;
	SPECULAR = sheen_strength;
	METALLIC = 0.0;
}
