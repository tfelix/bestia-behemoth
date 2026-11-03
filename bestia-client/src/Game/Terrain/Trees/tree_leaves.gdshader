// The leaf cards of every tree under Game/Terrain/Trees. Bound in place of the glTF's own leaf material by each
// model's import settings, so a tree drawn anywhere gets it without the renderer knowing.
//
// **Why the stock material looked black.** Each card is a flat quad lit by its own face normal, so in a crown of
// hundreds of them every card turned from the sun drops to ambient, and the crown shadows its own interior on
// top of that. Quaternius' previews use a foliage shader of his own that the free pack does not ship. This is
// ours, and it does what such shaders do: light the crown as one rounded mass rather than as its cards.
//
// The models stand on their own origin with the trunk up the y axis and no node transform, so model space is
// enough to know where in the crown a vertex sits - nothing per model has to be passed in.
shader_type spatial;
render_mode cull_disabled, diffuse_lambert, specular_disabled;

#include "res://Game/World/Shader/weather_cover.gdshaderinc"

uniform sampler2D leaf_texture : source_color, filter_linear_mipmap, repeat_disable;
uniform float alpha_cutoff : hint_range(0.0, 1.0) = 0.35;

group_uniforms crown;

// Model-space heights, in metres, of the bottom and the top of the crown for this family. Approximate on
// purpose: the five models of a family differ by a metre or two and only the gradient below reads them.
uniform float crown_low = 2.5;
uniform float crown_high = 8.5;

// How far a card's own normal is replaced by the crown's. 1 lights every card as the surface of one blob.
uniform float crown_normal_share : hint_range(0.0, 1.0) = 0.85;

// Colour at the foot and the inside of the crown, and at its sunlit top, multiplied into the texture.
uniform vec3 inner_tint : source_color = vec3(0.74, 0.80, 0.64);
uniform vec3 outer_tint : source_color = vec3(1.15, 1.18, 0.95);

group_uniforms light;

// Lambert wrapped past the terminator, so the side away from the sun shades down rather than switches off.
uniform float wrap : hint_range(0.0, 1.0) = 0.55;

// How much of the sun a card keeps in full shadow. The crown shadowing itself is what made the inside black.
uniform float shadow_floor : hint_range(0.0, 1.0) = 0.5;

// Sun showing through the leaves when the camera looks towards it.
uniform float translucency : hint_range(0.0, 2.0) = 0.5;

group_uniforms weather;

uniform vec3 snow_colour : source_color = vec3(0.92, 0.94, 0.96);
uniform float snow_melt_celsius : hint_range(-20.0, 20.0) = 1.0;
uniform float wet_darkening : hint_range(0.3, 1.0) = 0.7;

group_uniforms;

// 0 at the foot of the crown and 1 at its top.
varying float crown_v;

varying vec3 world_pos;

void vertex() {
	crown_v = smoothstep(crown_low, crown_high, VERTEX.y);

	// Out from the trunk at the foot of the crown, turning up towards its top: the normals of a rounded crown,
	// independent of how the cards happen to face.
	vec2 out_xz = VERTEX.xz / max(length(VERTEX.xz), 0.001);
	vec3 crown_normal = normalize(vec3(out_xz * (1.0 - 0.6 * crown_v), mix(0.15, 1.2, crown_v)));
	NORMAL = normalize(mix(NORMAL, crown_normal, crown_normal_share));

	world_pos = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xyz;
}

void fragment() {
	vec4 leaf = texture(leaf_texture, UV);
	ALPHA = leaf.a;
	ALPHA_SCISSOR_THRESHOLD = alpha_cutoff;

	// No back-face flip, unlike grass.gdshader: the normal is the crown's, so both sides of a card are meant to
	// face the same way.
	ALBEDO = leaf.rgb * COLOR.rgb * mix(inner_tint, outer_tint, crown_v);

	// The overlay skips these meshes (see WeatherOverlay.HasCutout), so the weather is drawn here.
	float up = (INV_VIEW_MATRIX * vec4(NORMAL, 0.0)).y;
	vec2 cover = weather_cover(world_pos, up, 1.0, snow_melt_celsius);
	ALBEDO = mix(ALBEDO * mix(1.0, wet_darkening, cover.y), snow_colour, cover.x);
}

void light() {
	// Softened for the sun only. An omni light's attenuation is its falloff too, and flooring that would draw
	// the edge of a torch's range as a hard line.
	float lit = LIGHT_IS_DIRECTIONAL ? mix(shadow_floor, 1.0, ATTENUATION) : ATTENUATION;
	float wrapped = clamp((dot(NORMAL, LIGHT) + wrap) / (1.0 + wrap), 0.0, 1.0);
	float through = pow(clamp(dot(VIEW, -LIGHT), 0.0, 1.0), 4.0) * translucency * ATTENUATION;

	// LIGHT_COLOR carries a factor of PI for physically based models, which this takes back out.
	DIFFUSE_LIGHT += LIGHT_COLOR * (wrapped * lit + through) / PI;
}
