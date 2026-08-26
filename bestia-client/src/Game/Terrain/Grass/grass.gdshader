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

// How far the gust field has travelled downwind, in radians of `WeatherState.GustReferenceMetres`.
//
// Integrated on the CPU rather than worked out here as `speed * TIME`, and that is not a preference. A wave
// written `sin(dot(p, dir) - speed * TIME)` travels at `speed + TIME * dspeed/dt`, so while the wind is
// ramping - which it does for twelve seconds after the first weather message - the error is proportional to
// how long the client has been running. It made the grass whip for the first seconds of every session and
// then settle, and the longer the player had sat in the menu the worse it was.
global uniform float weather_gust_phase;

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

// Distance between crests of the gust wave, in metres: how wide the ripple crossing a meadow is.
uniform float gust_wavelength : hint_range(0.5, 60.0) = 9.0;

// How much of the lean the gust takes away and gives back, 0 to 1. At 1 the grass comes fully upright
// between gusts; at 0 it holds a constant angle and only flutters.
uniform float gust_depth : hint_range(0.0, 1.0) = 0.45;

// Flicker across the wind, as a share of the lean, and how fast it runs. The part that reads as individual
// blades rather than as one sheet of cloth.
uniform float flutter_amount : hint_range(0.0, 1.0) = 0.18;
uniform float flutter_speed : hint_range(0.0, 20.0) = 4.5;

varying vec3 worldPos;

// 0 at the base of a blade and 1 at its tip, whichever way this mesh was unwrapped.
varying float blade_v;

void vertex() {
	blade_v = uv_v_at_tip ? 1.0 - UV.y : UV.y;

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

	// A plane wave travelling downwind. Its spatial phase comes off the world position, so neighbouring plants
	// are out of step with each other for free - which is what lets a whole chunk be one MultiMesh carrying
	// nothing per instance but a transform - and its temporal phase is the integral above.
	//
	// The wave crosses the ground at the wind's own speed exactly when `gust_wavelength` matches the reference
	// the phase was measured against. Retuning it here makes the waves travel proportionally faster, which is a
	// look and not a fault.
	float phase = TAU * dot(rest.xz, dir) / max(gust_wavelength, 0.001) - weather_gust_phase;
	float gust = 1.0 + gust_depth * sin(phase);

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
