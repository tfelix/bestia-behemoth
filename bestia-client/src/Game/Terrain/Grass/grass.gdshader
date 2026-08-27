/*
MIT License

Copyright (c) 2023 StayAtHomeDev

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

// The wind is Bestia's own, added on top of StayAtHomeDev's original. `weather_wind` comes from
// `WeatherState`; `StaticEntityRenderer` is what draws this and what the model-space note below is about.

shader_type spatial;
render_mode cull_disabled;

uniform vec3 color : source_color;
uniform vec3 color2 : source_color;
uniform sampler2D noise;
uniform float noiseScale = 20.0;

// Where the weather is going and how fast, in metres per second, in Godot's axes. Published by
// `WeatherState.Publish` and declared in project.godot's [shader_globals], which is also what gives it a
// value in the editor while there is no server to ask - the arrangement `weather_wetness` already has.
//
// Global rather than a per-material uniform because every plant in the world stands in the same wind, and a
// material uniform would have to be pushed to one material per kind on every weather message.
global uniform vec3 weather_wind;

// How far the gust field has been carried downwind, in metres, in Godot's axes: the running integral of
// `weather_wind`. Published by `WeatherState._Process` and declared in project.godot's [shader_globals], so
// it has a value in the editor while there is no server to ask.
//
// **A displacement and not a phase, and that is what stopped the grass whipping whenever the wind turned.**
// The gust used to be one plane wave, `sin(TAU * dot(p, dir) / wavelength - phase)`, with `dir` the current
// bearing and `p` the plant's world position - a wave field rigidly pinned to the world origin. Turning
// `dir` therefore swept the entire field about that origin, and a plant at radius `r` saw its phase move by
// `TAU * r * dtheta / wavelength`. The origin is an arbitrary corner of the map: stood in the middle of a
// 128 km world, `r` is about 90 km, so a one-radian veer moved the phase through sixty thousand radians.
// Spread over the twelve seconds `WeatherState` takes to swing the wind that is hundreds of hertz of pure
// aliasing, falling to something merely visible after a minute and a half. `/date` re-rolls the bearing
// outright and so is the loudest way to hear it, but the two-minute weather heartbeat was doing the same
// thing at a few hertz all along.
//
// Advection has no such term. The field below is a fixed shape in world space that the wind only ever
// slides, so a bearing change alters where the field is *going* and never where it *is*: continuous however
// hard the wind turns, and the same wherever in the world the player stands. `cloud_shadows.gd` drifts its
// mask this way already, which is why cloud shadows never had this fault.
//
// Still integrated on the CPU rather than taken as `wind * TIME` here, which is the older half of the same
// lesson: anything written against `TIME` travels at `speed + TIME * dspeed/dt`, so while the wind ramps the
// error grows with how long the client has been open.
global uniform vec2 weather_gust_drift;

group_uniforms mesh;

// True when this mesh's V runs 1 at the base and 0 at the tip, which `grass2` does and `grass` does not.
//
// A statement about the mesh rather than a look knob, and it has to be one because the two were unwrapped in
// opposite directions and neither is wrong. Getting it backwards is visible twice over: the blade is dark at
// the tip and bright at the root, and it bends from the wrong end.
uniform bool uv_v_at_tip = false;

group_uniforms wind;

// Lean at the tip as a share of the plant's own height, once the wind is hard enough to saturate. 0.45 is
// about 25 degrees off vertical - a stiff breeze, not a storm laying a meadow flat.
uniform float wind_max_lean : hint_range(0.0, 1.5) = 0.45;

// The wind speed, in m/s, at which the lean reaches about 63% of `wind_max_lean`.
//
// The saturation is the point rather than this curve's exact shape. Wind speed is a server number with no
// ceiling declared on it, and `WeatherLook.WindScale` multiplies a tornado's by a further 1.8 - so a lean
// proportional to it would push the tips of a reed bed through the ground and out the other side.
uniform float wind_reference_speed : hint_range(0.5, 40.0) = 9.0;

// Metres before the gust field repeats itself, in both x and z: how wide the patchwork crossing a meadow is.
//
// A period rather than a wavelength, because the field is three waves and not one. Each wave's own crest
// spacing is this over the length of its integer wave number below - about a fifth of it - so 45 m here is
// the 9 m ripple the single wave used to draw. It has to stay a whole-field period: the waves are integers
// over it precisely so that `fract` can fold both the position and the drift into one tile without moving
// the field, which is what keeps float32 honest millions of metres from the world origin.
uniform float gust_period : hint_range(5.0, 300.0) = 45.0;

// How much of the lean the gust takes away and gives back, 0 to 1. At 1 the grass comes fully upright
// between gusts; at 0 it holds a constant angle and only flutters.
uniform float gust_depth : hint_range(0.0, 1.0) = 0.45;

// Flicker across the wind, as a share of the lean, and how fast it runs. The part that reads as individual
// blades rather than as one sheet of cloth.
uniform float flutter_amount : hint_range(0.0, 1.0) = 0.18;
uniform float flutter_speed : hint_range(0.0, 20.0) = 4.5;

group_uniforms;

// How much bigger to draw this material's plants than they were authored, as a share of their own size. Set by
// `TerrainGrass` so that a cell thinned by distance still covers its ground - see `GrassLod.CoverageScale`.
//
// **Additive, so that not setting it is a no-op.** `StaticEntityRenderer` draws every ground-cover prop in the
// world with this same shader and sets nothing, and a multiplier those instances read as zero would collapse
// every herb, shrub and reed to a point. Nought means unchanged.
//
// **A material uniform and not an `instance uniform`, and that is a hard limit rather than a preference.**
// Godot gives every instance that has ever been handed an instance uniform a fixed sixteen-slot block out of
// the one buffer `rendering/limits/global_shader_variables/buffer_size` sizes - 65536 entries by default, so
// 4096 instances for the whole game - and it never takes a block back until that instance is freed. The field
// is one multimesh per 8 m cell per layer, which is 32 per chunk across a 121-chunk view volume, so writing
// this per node ran the buffer dry within twenty seconds of login: `global_shader_parameters_instance_allocate`
// then returns -1 and every grass cell raised after that draws at its authored size for the rest of the
// session. `TerrainGrass` quantises the scale and keeps one shared material per step instead - a few dozen
// materials for the whole world, and no per-instance allocation at all.
uniform float grass_extra_scale = 0.0;

varying vec3 worldPos;

// 0 at the base of a blade and 1 at its tip, whichever way this mesh was unwrapped.
varying float blade_v;

void vertex() {
	blade_v = uv_v_at_tip ? 1.0 - UV.y : UV.y;

	// Grown about the plant's own base, which both meshes are authored standing on, so a scaled clump stays
	// planted instead of sinking or floating. Done before `rest` below rather than after, so that everything
	// measured from it follows for free: `above` is the taller lever arm, and a clump drawn at twice its size
	// sways like the taller plant it now is rather than like the one it was authored as.
	VERTEX *= 1.0 + grass_extra_scale;

	// Where this vertex would stand in dead calm. Kept, because the offset has to be turned back into model
	// space at the end and because the albedo noise below is a property of the ground, not of the blade.
	vec3 rest = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xyz;
	worldPos = rest;

	// Metres above this plant's own base, in world units, so the bend scales with the instance rather than
	// with the mesh: a 1.8 m reed sways further than a 0.45 m herb without either being told its height.
	// Exact because both meshes are authored standing on y = 0, so the instance origin *is* the base.
	float above = max(rest.y - MODEL_MATRIX[3].y, 0.0);

	float speed = length(weather_wind);

	// Guarded rather than branched: at dead calm the bearing is arbitrary and `lean` below is zero, so any
	// unit vector will do and a branch would only cost a divergence.
	vec2 dir = weather_wind.xz / max(speed, 0.001);
	vec2 across = vec2(-dir.y, dir.x);

	float lean = wind_max_lean * (1.0 - exp(-speed / max(wind_reference_speed, 0.001)));

	// The gust field: a fixed patchwork of stronger and weaker wind, slid bodily downwind by the drift above.
	// Its shape comes off the world position, so neighbouring plants are out of step with each other for free
	// - which is what lets a whole chunk be one MultiMesh carrying nothing per instance but a transform.
	//
	// It crosses the ground at exactly the wind's own velocity, for any bearing and whatever `gust_period` is
	// set to, because the drift is measured in metres rather than in radians of an assumed wavelength.
	//
	// **Three waves rather than one, and they are why the crests no longer follow the wind round.** A single
	// wave has to be *oriented*, and orienting it by the bearing is the pinned-to-the-origin fault the drift
	// above documents. Three fixed bearings need no orientation and, being non-parallel, cannot all be
	// broadside to the drift at once - so the field always travels, whichever way the wind blows, which one
	// fixed wave would not. What is lost is crests square to the wind, and that is not something the eye can
	// find: what it reads is the *lean*, and the lean is downwind here as it always was.
	//
	// The wave numbers are integer vectors, which is what makes the field exactly periodic on `gust_period`
	// in x and z and so what makes the `fract` below free. Their lengths are 5.0 to 5.4, so the three crest
	// spacings are 8.4 to 9.0 m at the default period - the 9 m ripple this replaces. Their bearings are 11,
	// 68 and 127 degrees: within a few degrees of 60 apart, so the field has no grain of its own, and none of
	// them axis-aligned, so nothing beats with the lattice the plants are scattered on.
	float period = max(gust_period, 0.001);

	// Both terms folded into one tile before anything is multiplied out. Subtracting a whole number of periods
	// from either leaves every wave below untouched - that is what integer wave numbers buy - so this is exact
	// and not an approximation, and it keeps the arithmetic in the first few metres rather than the first few
	// million.
	//
	// Which matters at both ends, and only past a million metres. `WorldConfig` allows a world 4096 km across,
	// where a plant near the middle stands at 2.9e6 metres; and the drift above is never wrapped, so a long
	// session in a stiff wind reaches the same order. Either puts the phase past 2e4 periods, where one float32
	// step is no smaller than the tenth of a metre a frame of drift adds - so the gust would crawl in stutters
	// rather than move. The 128 km dev world is nowhere near that, and this costs two instructions.
	vec2 tile = fract(rest.xz / period) - fract(weather_gust_drift / period);

	// Weighted so the sum has the same RMS as the single sine it replaces, which is what keeps `gust_depth`
	// meaning what it meant. Clamped because three waves in step reach 1.7, and `gust_depth` promises a bound:
	// the clip lands on the tails of the distribution and reads as a gust holding its peak.
	float field =
		0.70 * sin(TAU * dot(tile, vec2(5.0, 1.0))) +
		0.55 * sin(TAU * dot(tile, vec2(2.0, 5.0)) + 1.7) +
		0.45 * sin(TAU * dot(tile, vec2(-3.0, 4.0)) + 3.9);

	float gust = 1.0 + gust_depth * clamp(field, -1.0, 1.0);

	// Deliberately incommensurate multipliers on x and z: a flutter keyed to round numbers would beat with
	// the three-metre lattice the plants are scattered on and read as rows.
	float flutter = sin(TIME * flutter_speed + rest.x * 3.7 + rest.z * 2.3);

	// `above` is the lever arm and `blade_v` bends the blade over it, so a blade curves away from its own
	// root instead of tipping like a plank, and a vertex on the ground does not move at all.
	vec2 offset = above * blade_v * lean * (dir * gust + across * flutter * flutter_amount);

	vec3 world = rest;
	world.xz += offset;

	// Blades keep their length: a tip that leaned out without coming down would grow as the wind rose. The
	// small-angle drop of a rod of length `above` swung through `offset`, guarded at the root where the arm
	// is zero and nothing has moved anyway.
	world.y -= 0.5 * dot(offset, offset) / max(above, 0.001);

	// Back into model space, which is where VERTEX lives. Not a general inverse: every transform this is
	// drawn with is a yaw and a uniform scale - see `StaticEntityRenderer.AddMeshBatch` - so the basis
	// columns are orthogonal and the inverse is the transpose over each column's squared length. The `max`
	// guards the zero basis that a collected plant's slot is parked at.
	vec3 moved = world - rest;
	vec3 c0 = MODEL_MATRIX[0].xyz;
	vec3 c1 = MODEL_MATRIX[1].xyz;
	vec3 c2 = MODEL_MATRIX[2].xyz;

	VERTEX += vec3(
		dot(moved, c0) / max(dot(c0, c0), 0.000001),
		dot(moved, c1) / max(dot(c1, c1), 0.000001),
		dot(moved, c2) / max(dot(c2, c2), 0.000001));
}

void fragment() {
	vec3 noiseLevel = texture(noise, worldPos.xz / noiseScale).rgb;
	ALBEDO = mix(color, color2, 1.0 - blade_v) * mix(color, color2, noiseLevel.r);
	if (!FRONT_FACING) {
		NORMAL = -NORMAL;
	}
}
