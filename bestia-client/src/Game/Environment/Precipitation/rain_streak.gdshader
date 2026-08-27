// Lays a rain quad along the wind, as the camera sees it.
//
// **This is what `TRANSFORM_ALIGN_Z_BILLBOARD_Y_TO_VELOCITY` was doing, and why it stopped being enough.**
// That mode orients the quad from the particle's own velocity and then turns it to face the camera, which is
// two requirements that fight each other the moment the rain is heading where the camera is looking. The
// spring arm pitches to a half-turn down (`min_vertical_angle` is -PI/2) and rain at a stiff wind falls about
// 23 degrees off vertical, so the two line up within a few degrees on any steep camera - and there the
// velocity has almost nothing left in the screen plane to point the streak with. What survives is the 3-degree
// emission spread, so every drop picks its own near-arbitrary angle and the field stops leaning with the wind
// at all. That is the "strange when it blows away from you" fault this replaces.
//
// The direction here is a uniform rather than a per-particle velocity, which is the whole fix: every drop in
// the sky is falling the same way, so the field leans as one sheet and cannot disagree with itself. A drop
// heading at or away from the camera then *foreshortens* rather than spinning - which is what rain coming
// towards you actually looks like - down to `min_foreshortening`, since a streak of no length is a drop that
// has vanished.
//
// Drawn with `transform_align` off. The particle transform is then a translation and a uniform scale with no
// rotation of its own, which is what lets the whole orientation be built here from the camera basis.
shader_type spatial;
render_mode unshaded, cull_disabled;

// Albedo and opacity before the particle's own fade ramp, which arrives as the vertex colour.
uniform vec4 tint : source_color = vec4(0.70, 0.77, 0.88, 0.40);

// Unit vector the rain is travelling along, in world space: the wind's horizontal carry over the fall speed.
// Written by `precipitation.gd` whenever it retunes the emitter, which is the same moment it writes
// `ParticleProcessMaterial.direction` - so the streak and the travel can never be pointing different ways.
uniform vec3 rain_direction = vec3(0.0, -1.0, 0.0);

// Shortest a head-on streak is allowed to get, as a share of its authored length.
//
// A quarter, so rain falling straight down a straight-down camera is a short tick rather than nothing. It is
// also what keeps the direction below stable: at exactly zero the streak would have no length to be wrong
// about, but the frames either side of it would still be dividing by very little.
uniform float min_foreshortening : hint_range(0.0, 1.0) = 0.25;

void vertex() {
	// The camera's own axes, in world space. `INV_VIEW_MATRIX` is view -> world, so its first two columns are
	// screen right and screen up and the plane they span is the plane a billboard has to lie in.
	vec3 right = INV_VIEW_MATRIX[0].xyz;
	vec3 up = INV_VIEW_MATRIX[1].xyz;

	// Where the rain is going, in screen coordinates. Not normalised: what is thrown away by the projection is
	// exactly the part of the travel that runs into the screen, so what is left over is both the direction the
	// streak should lie along *and* how much of its length the camera can still see.
	vec3 fall = normalize(rain_direction);
	vec2 seen = vec2(dot(fall, right), dot(fall, up));
	float across_screen = length(seen);

	// Straight at or away from the camera. Any direction will do for a streak this short, and down-screen is
	// the one that reads as rain.
	vec2 along = across_screen > 0.001 ? seen / across_screen : vec2(0.0, -1.0);
	vec2 sideways = vec2(-along.y, along.x);

	// The mesh is a `QuadMesh`, so VERTEX.x is its width and VERTEX.y its length. Only the length foreshortens:
	// a drop seen end-on is a shorter streak and not a thinner one.
	vec2 laid = sideways * VERTEX.x + along * (VERTEX.y * max(across_screen, min_foreshortening));

	// Back into the model space VERTEX lives in. No division by the particle's scale: it is uniform, so it
	// multiplies the offset by the same factor whichever way the offset points, and the streak grows with the
	// drop as `scale_min`/`scale_max` intend.
	VERTEX = right * laid.x + up * laid.y;
}

void fragment() {
	ALBEDO = tint.rgb * COLOR.rgb;
	ALPHA = tint.a * COLOR.a;
}
