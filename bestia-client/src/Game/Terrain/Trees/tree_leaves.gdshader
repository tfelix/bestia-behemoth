// The leaf cards of every tree under Game/Terrain/Trees. Bound in place of the glTF's own leaf material by each
// model's import settings, so a tree drawn anywhere gets it without the renderer knowing.
//
// **Why the stock material looked black.** Each card is a flat quad lit by its own face normal, so in a crown of
// hundreds of them every card turned from the sun drops to ambient, and the crown shadows its own interior on
// top of that. Quaternius' previews use a foliage shader of his own that the free pack does not ship. This is
// ours, and it does what such shaders do: light the crown as one rounded mass rather than as its cards.
//
// **It colours the leaves itself.** The pack ships each leaf texture twice: a white mask (`Leaves_NormalTree`,
// `Leaf_Pine`), meant for exactly this, and a flat pre-tinted `_C` copy that the glTFs reference. The `_C` copy
// is one green everywhere, which is most of why the stock trees read flatter than the previews. Here the mask
// gives the shape and the crown gives the colour: deep at the foot, bright at the top, warm where the sun hits,
// and a little different per tree.
//
// The models stand on their own origin with the trunk up the y axis and no node transform, so model space is
// enough to know where in the crown a vertex sits - nothing per model has to be passed in.
shader_type spatial;
render_mode cull_disabled, diffuse_lambert, specular_disabled;

#include "res://Game/World/Shader/weather_cover.gdshaderinc"

// The leaf shapes: white where there is a leaf, its alpha the cutout.
uniform sampler2D leaf_mask : source_color, filter_linear_mipmap, repeat_disable;
uniform float alpha_cutoff : hint_range(0.0, 1.0) = 0.35;

group_uniforms crown;

// Model-space heights, in metres, of the bottom and the top of the crown for this family. Approximate on
// purpose: the five models of a family differ by a metre or two and only the gradients below read them.
uniform float crown_low = 2.5;
uniform float crown_high = 8.5;

// How far a card's own normal is replaced by the crown's. 1 lights every card as the surface of one blob.
uniform float crown_normal_share : hint_range(0.0, 1.0) = 0.85;

// The leaves' colour at the foot and the inside of the crown, and at its top.
uniform vec3 colour_low : source_color = vec3(0.24, 0.42, 0.10);
uniform vec3 colour_high : source_color = vec3(0.62, 0.80, 0.22);

// How far one tree's colour strays from its neighbours', in brightness and towards yellow. Keyed to where it
// stands, so a forest is not one green and a tree keeps its shade across re-streams.
uniform float tree_variation : hint_range(0.0, 0.5) = 0.18;

group_uniforms light;

// Lambert wrapped past the terminator, so the side away from the sun shades down rather than switches off.
uniform float wrap : hint_range(0.0, 1.0) = 0.55;

// How much of the sun a card keeps in full shadow. The crown shadowing itself is what made the inside black.
uniform float shadow_floor : hint_range(0.0, 1.0) = 0.5;

// Sun showing through the leaves when the camera looks towards it.
uniform float translucency : hint_range(0.0, 2.0) = 0.5;

// Multiplied into the sun on the faces it hits squarely: the warm, yellowed highlight of a painted canopy. Not a
// source colour, because it is a gain and goes above one.
uniform vec3 sun_tint = vec3(1.18, 1.10, 0.78);

group_uniforms wind;

// How far the top of a crown sways in a wind hard enough to saturate, in metres of the model as authored.
uniform float sway_metres : hint_range(0.0, 2.0) = 0.35;

// The wind speed, in m/s, at which the sway reaches about 63% of `sway_metres` - grass.gdshader's curve.
uniform float wind_reference_speed : hint_range(0.5, 40.0) = 9.0;

uniform float sway_speed : hint_range(0.0, 5.0) = 1.1;

group_uniforms camera;

// Leaves closer to the camera than this, in metres, are dithered out, and are whole again by the far end. The
// camera rides 10 to 45 m behind the player, so a crown between the two otherwise fills the screen with cards
// a few metres from the lens.
uniform float near_fade_from = 2.0;
uniform float near_fade_to = 5.0;

group_uniforms weather;

uniform vec3 snow_colour : source_color = vec3(0.92, 0.94, 0.96);
uniform float snow_melt_celsius : hint_range(-20.0, 20.0) = 1.0;
uniform float wet_darkening : hint_range(0.3, 1.0) = 0.7;

group_uniforms;

// Where the weather is going and how fast, in m/s - the same global the grass sways in. See grass.gdshaderinc.
global uniform vec3 weather_wind;

// 0 at the foot of the crown and 1 at its top.
varying float crown_v;

// 0 to 1, one per tree.
varying flat float tree_seed;

varying vec3 world_pos;

void vertex() {
	crown_v = smoothstep(crown_low, crown_high, VERTEX.y);

	vec3 p3 = fract(vec3(mod(MODEL_MATRIX[3].xz, 512.0).xyx) * 0.1031);
	p3 += dot(p3, p3.yzx + 33.33);
	tree_seed = fract((p3.x + p3.y) * p3.z);

	// Out from the trunk at the foot of the crown, turning up towards its top: the normals of a rounded crown,
	// independent of how the cards happen to face.
	vec2 out_xz = VERTEX.xz / max(length(VERTEX.xz), 0.001);
	vec3 crown_normal = normalize(vec3(out_xz * (1.0 - 0.6 * crown_v), mix(0.15, 1.2, crown_v)));
	NORMAL = normalize(mix(NORMAL, crown_normal, crown_normal_share));

	// The crown leans downwind and rocks about that lean, more the higher up it is. In model space and along the
	// wind turned into it, so the sway scales with the tree: the renderer draws these with a yaw and a uniform
	// scale only, which makes the transpose of the basis, over its squared length, its inverse.
	float speed = length(weather_wind);
	vec3 wind_dir = vec3(weather_wind.x, 0.0, weather_wind.z) / max(speed, 0.001);
	vec3 c0 = MODEL_MATRIX[0].xyz;
	vec3 c2 = MODEL_MATRIX[2].xyz;
	vec2 model_dir = vec2(dot(wind_dir, c0) / max(dot(c0, c0), 0.000001), dot(wind_dir, c2) / max(dot(c2, c2), 0.000001));
	float lean = sway_metres * (1.0 - exp(-speed / max(wind_reference_speed, 0.001)));
	float rock = 0.65 + 0.35 * sin(TIME * sway_speed + tree_seed * TAU);
	float flutter = 0.08 * sin(TIME * sway_speed * 5.3 + VERTEX.x * 2.1 + VERTEX.z * 1.7);
	VERTEX.xz += model_dir * length(c0) * lean * crown_v * crown_v * (rock + flutter);

	world_pos = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xyz;
}

void fragment() {
	vec4 leaf = texture(leaf_mask, UV);
	ALPHA = leaf.a;
	ALPHA_SCISSOR_THRESHOLD = alpha_cutoff;

	// Measured from the camera in world space rather than off VERTEX, so the shadow pass, whose camera is the
	// sun's and far away, never thins a tree's shadow.
	float near = smoothstep(near_fade_from, near_fade_to, length(world_pos - CAMERA_POSITION_WORLD));
	if (fract(52.9829189 * fract(dot(FRAGCOORD.xy, vec2(0.06711056, 0.00583715)))) >= near) {
		discard;
	}

	// No back-face flip, unlike grass.gdshader: the normal is the crown's, so both sides of a card are meant to
	// face the same way.
	vec3 crown = mix(colour_low, colour_high, crown_v);
	float jitter = (tree_seed - 0.5) * 2.0 * tree_variation;
	crown *= 1.0 + jitter;
	crown = mix(crown, crown * vec3(1.12, 1.05, 0.7), max(jitter, 0.0));
	ALBEDO = leaf.rgb * COLOR.rgb * crown;

	// The overlay skips these meshes (see WeatherOverlay.HasCutout), so the weather is drawn here.
	float up = (INV_VIEW_MATRIX * vec4(NORMAL, 0.0)).y;
	vec2 cover = weather_cover(world_pos, up, 1.0, snow_melt_celsius);
	ALBEDO = mix(ALBEDO * mix(1.0, wet_darkening, cover.y), snow_colour, cover.x);

	// specular_disabled drops only the sun's highlight. The sky's reflection still lands on every card, and on the
	// shaded side of a crown it outshines the green and turns it slate blue.
	SPECULAR = 0.0;
}

void light() {
	// Softened for the sun only. An omni light's attenuation is its falloff too, and flooring that would draw
	// the edge of a torch's range as a hard line.
	float lit = LIGHT_IS_DIRECTIONAL ? mix(shadow_floor, 1.0, ATTENUATION) : ATTENUATION;
	float wrapped = clamp((dot(NORMAL, LIGHT) + wrap) / (1.0 + wrap), 0.0, 1.0);
	float through = pow(clamp(dot(VIEW, -LIGHT), 0.0, 1.0), 4.0) * translucency * ATTENUATION;
	vec3 warm = mix(vec3(1.0), sun_tint, smoothstep(0.55, 1.0, wrapped) * ATTENUATION);

	// LIGHT_COLOR carries a factor of PI for physically based models, which this takes back out.
	DIFFUSE_LIGHT += LIGHT_COLOR * warm * (wrapped * lit + through) / PI;
}
