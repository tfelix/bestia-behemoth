shader_type spatial;
render_mode blend_mix, cull_back, depth_draw_opaque;

// Something part-built: solid up to the line the work has reached, a translucent green scaffold above it.
//
// The line is in *model* space rather than world space, so a site keeps looking the same wherever it stands
// and however the terrain under it slopes. `y_min` and `y_span` come off the mesh's own AABB, which the
// caller reads once - a shader cannot see it, and normalising against a guessed height puts the line in the
// wrong place for every kind but one. See PropVisualBuilder.BuildConstruction.

// 0 is a bare marker, 1 is finished. A placement ghost sits at 0 forever.
uniform float progress : hint_range(0.0, 1.0) = 0.0;

uniform float y_min = 0.0;
uniform float y_span = 1.0;

// What the finished thing is made of. The placeholder colour of its kind today.
uniform vec4 built_color : source_color = vec4(0.55, 0.40, 0.24, 1.0);

uniform vec4 ghost_color : source_color = vec4(0.35, 0.90, 0.45, 0.35);

// How much of the model the line is smeared over, so the boundary sweeps rather than steps.
uniform float blend_band : hint_range(0.001, 0.5) = 0.06;

varying float height_fraction;

void vertex() {
	height_fraction = (VERTEX.y - y_min) / max(y_span, 0.0001);
}

void fragment() {
	// 0 well above the line, 1 well below it.
	float built = smoothstep(progress + blend_band, progress - blend_band, height_fraction);

	ALBEDO = mix(ghost_color.rgb, built_color.rgb, built);
	ALPHA = mix(ghost_color.a, built_color.a, built);

	// The scaffold glows faintly so it reads as a marker rather than as tinted glass, and stops glowing as
	// the real thing takes over.
	EMISSION = ghost_color.rgb * (1.0 - built) * 0.25;
}
