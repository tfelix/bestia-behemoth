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

// Included by grass.gdshader and grass_dissolve.gdshader, which differ only in GRASS_DISSOLVE.

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

// Grass sways in its own vertex shader, which the object overlay cannot follow, so it reads the weather here.
global uniform float weather_wetness;
#include "res://Game/World/Shader/weather_snow.gdshaderinc"

uniform vec3 snow_colour : source_color = vec3(0.92, 0.94, 0.96);
uniform float snow_melt_celsius : hint_range(-20.0, 20.0) = 1.0;

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

// Applies `grass_extra_scale` across the ground only, so a far tuft standing in for thinned neighbours covers
// their ground without standing taller than they did. Set by `TerrainGrass.WidenNotGrow`.
uniform bool grass_widen_only = false;

// How far a blade still growing in at the reveal front takes the colour of the ground under it, so it appears
// against a background it matches. Set by `TerrainGrass.GroundMatch`; nought is the blade's own colour.
uniform float grass_ground_match : hint_range(0.0, 1.0) = 0.0;

// Where this cell's reveal has reached, in shares of its own instance order, and how wide the ramp behind that
// front is. Written per cell by `TerrainGrass.Retune`; the arithmetic is `GrassLod.RevealFront`, and the two
// lines below transcribe `GrassLod.Reveal`.
//
// The multimesh can only truncate its tail, and `TerrainGrass.Scatter` shuffles each cell precisely so that the
// prefix left standing is a uniform subset of the ground. That is what makes `INSTANCE_ID` enough on its own:
// it already says where a blade sits relative to the front, so nothing has to be stored per instance.
//
// **Nought is a cell nobody is revealing**, for the same reason `grass_extra_scale` is additive: every herb,
// shrub and reed in the world is drawn by `StaticEntityRenderer` with this shader and sets no instance uniform
// at all. An unset one resolves to the default declared here, so the default has to mean "already grown".
instance uniform float grass_instance_step = 0.0;
instance uniform float grass_reveal = 0.0;

// A share of the cell rather than a count of blades, so a blade takes the same *time* to grow whether its cell
// holds thirty of them or three hundred - how fast the front moves is a property of the player's walking speed
// and the taper, not of the density. It costs the field a little coverage in the taper, and much less than its
// width suggests: at 0.06 the tuft band is short by at most 0.03 of a cell. See `GrassLod.RevealedFraction`.
uniform float grass_reveal_span : hint_range(0.005, 0.2) = 0.06;

group_uniforms burning;

// Set by `TerrainGrass` only. The herbs, shrubs and reeds drawn with this shader are server props that the
// fire kills on its own, so they must not burn down a second time here.
uniform bool burns_with_ground = false;

// `GroundMarkTexture` (scorch is R) and `GroundFireTexture`, sampled at the plant's own square metre.
uniform sampler2D ground_marks : filter_nearest, repeat_enable, hint_default_transparent;
uniform sampler2D ground_fire : filter_nearest, repeat_enable, hint_default_transparent;
uniform float ground_marks_extent = 512.0;

uniform vec3 burnt_colour : source_color = vec3(0.035, 0.03, 0.025);
uniform vec3 ember_colour : source_color = vec3(1.0, 0.36, 0.06);
uniform float ember_energy : hint_range(0.0, 16.0) = 4.0;

group_uniforms;

// Seconds on the clock fire records are stamped in. See `GroundFireClock`.
global uniform float ground_fire_clock;

// From catching fire to gone, shared with the terrain so ground and grass char together. Under the server's
// 4.8 s burn (`GroundFireConfig`), so both are done before the scar replaces the fire record.
global uniform float ground_fire_burn_seconds;

varying vec3 worldPos;

// 0 for a plant untouched by fire, 1 for one burnt away.
varying flat float burn;

// 1 while the plant's square metre is alight, which is when the edge of the char glows.
varying flat float alight;

// A random number per plant, so each one's embers flicker on their own and no wave rolls across the field.
varying flat float plant_seed;

// 0 at the base of a blade and 1 at its tip, whichever way this mesh was unwrapped.
varying float blade_v;

// How grown this blade is at the reveal front, 0 to 1. Drawn as size, as a dissolve, or as a fade into the ground.
varying flat float reveal_grow;

void vertex() {
	blade_v = uv_v_at_tip ? 1.0 - UV.y : UV.y;

	burn = 0.0;
	alight = 0.0;
	plant_seed = 0.0;
	if (burns_with_ground) {
		vec3 p3 = fract(vec3(mod(MODEL_MATRIX[3].xz, 256.0).xyx) * 0.1031);
		p3 += dot(p3, p3.yzx + 33.33);
		plant_seed = fract((p3.x + p3.y) * p3.z);

		vec2 cell = MODEL_MATRIX[3].xz / ground_marks_extent;
		vec2 fire = texture(ground_fire, cell).rg;

		if (fire.r > 0.0) {
			burn = clamp((ground_fire_clock - fire.r) / max(ground_fire_burn_seconds, 0.001), 0.0, 1.0);
			alight = fire.g > 0.0 ? 0.0 : 1.0;
		} else if (texture(ground_marks, cell).r > 0.5) {
			burn = 1.0;
		}
	}

	// How grown this blade is: nothing at the reveal front, full size a span behind it. The half-instance
	// offset centres a blade in its own slot, which is what makes this agree with the count the multimesh was
	// truncated to - the blade about to wink out is already too small to see, and the next one is not drawn.
	//
	// The branch is uniform across the draw call, so it costs no divergence.
	float mine = (float(INSTANCE_ID) + 0.5) * grass_instance_step;
	float grow = grass_instance_step > 0.0
		? clamp((grass_reveal - mine) / max(grass_reveal_span, 0.0001), 0.0, 1.0)
		: 1.0;

	reveal_grow = grow;

#ifdef GRASS_DISSOLVE
	// Drawn at full size; the fragment stage dissolves it in instead.
	grow = 1.0;
#endif

	// Grown about the plant's own base, which both meshes are authored standing on, so a scaled clump stays
	// planted instead of sinking or floating. Done before `rest` below rather than after, so that everything
	// measured from it follows for free: `above` is the taller lever arm, and a clump drawn at twice its size
	// sways like the taller plant it now is rather than like the one it was authored as.
	//
	// The same argument is what makes the reveal cost nothing downstream. `above` scales with `grow`, so the
	// whole displacement does: a half-grown blade bends through the same *angle* and moves half as far, which
	// is what a shorter blade should do. At `grow` of nought the offset is zero, which the length-preserving
	// drop below already guards for. Uniformly, never a squash in y - that would flatten the splayed blades of
	// a clump into a disc lying on the ground, which from this camera is worse than the pop it would fix.
	float extra = 1.0 + grass_extra_scale;
	VERTEX *= grow * (1.0 - smoothstep(0.25, 1.0, burn));
	VERTEX *= grass_widen_only ? vec3(extra, 1.0, extra) : vec3(extra);

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

#ifdef GRASS_DISSOLVE
	// Interleaved gradient noise: a screen-space threshold with no visible pattern, so a half-grown blade shows
	// half its pixels.
	if (fract(52.9829189 * fract(dot(FRAGCOORD.xy, vec2(0.06711056, 0.00583715)))) >= reveal_grow) {
		discard;
	}
#endif

	// The blade's own root colour, which is what the terrain's grass canopy is matched to.
	vec3 ground = color2 * mix(color, color2, noiseLevel.r);
	ALBEDO = mix(ALBEDO, ground, (1.0 - reveal_grow) * grass_ground_match);

	// The char climbs from the root and reaches the tip at 60% of the burn, with embers along its edge.
	float char_front = burn * 1.6;
	float charred = 1.0 - smoothstep(char_front - 0.2, char_front, blade_v);
	float edge = charred * (1.0 - charred) * 4.0;
	float flicker = 0.6 + 0.4 * sin(TIME * (9.0 + 6.0 * plant_seed) + plant_seed * 40.0);

	ALBEDO = mix(ALBEDO, burnt_colour, charred);

	// Snow settles on the upper blade first; wet blades darken and catch the light.
	float snowed = weather_snow_cover(worldPos.y, snow_melt_celsius) * smoothstep(0.2, 0.9, blade_v);
	float wet = clamp(weather_wetness, 0.0, 1.0) * (1.0 - snowed);
	ALBEDO = mix(ALBEDO * mix(1.0, 0.75, wet), snow_colour, snowed);
	ROUGHNESS = mix(1.0, 0.35, wet);
	EMISSION = ember_colour * ember_energy * edge * alight * flicker;
	if (!FRONT_FACING) {
		NORMAL = -NORMAL;
	}
}
