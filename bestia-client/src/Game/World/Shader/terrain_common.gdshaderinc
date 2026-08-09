// The master terrain shader, minus its output stage.
//
// Everything the shipping shader and the debug shader share lives here, which is all of it: they differ only in
// what they do with the result. That split is not tidiness. A debug_view uniform in the shipping shader keeps
// every branch of every view compiled into the hot path, and the texture fetches inside them alive, for a
// feature nobody has running - so the debug views live in a second shader that costs nothing when it is not
// loaded, and this file guarantees the two cannot drift.
//
// WHAT THIS SHADER IS GIVEN, AND WHAT IT IS NOT
//
// The mesh has no UVs and no tangents. Surface nets emits positions, normals, a tint and the slot weights, and
// nothing else - so texture coordinates are derived from world position here, and triplanar mapping is the only
// option rather than a choice between it and a UV path.
//
// It is also never told what biome it is in, because the server never says. The materialiser already decided:
// a beach is sand because the generator capped those columns with sand, and the mesher turned that into a
// weight. So there is no biome branching here and there is nowhere for any to go, which is the property that
// keeps this one shader for the whole world.
//
// SLOTS
//
// Eight fixed texture layers, weighted per vertex - not four material indices plus four weights. On a
// surface-nets mesh one vertex is shared by every quad around it, so a triangle routinely spans three
// materials, and an index interpolated across it means nothing. Fixing the layer to the channel makes
// interpolation exactly right instead: three corners of grass, sand and rock become a real three-way blend.
//
// The layer order is BlockAppearance.SurfaceSlot's ordinals and is a wire format between that file and this
// one:
//
//     0 NEUTRAL   1 GRASS   2 DRY_GRASS   3 SAND   4 SOIL   5 ROCK   6 SNOW   7 (reserved)

// The slot count is written as a literal 8 throughout rather than as a name.
//
// Not a style choice - Godot's shader preprocessor only runs for a shader loaded from a path, and it gives up
// on the whole file if it dislikes any directive, silently handing the raw text with its '#'s to the tokenizer.
// The failure is reported at the '#' rather than at the cause, so preprocessor use here is worth keeping to the
// one directive that earns it, which is #include. See TERRAIN_SLOTS in BlockAppearance for the authority on
// this number; anything that changes it changes the vertex format anyway.

// Below this a slot contributes less than half a byte of weight and is not worth a texture fetch.
const float TERRAIN_WEIGHT_EPSILON = 0.002;

// Below this a triplanar axis contributes less than the dither in an 8-bit channel. Flat ground is almost
// entirely one axis, so this is what keeps the common case at one fetch per slot rather than three.
const float TERRAIN_AXIS_EPSILON = 0.001;

group_uniforms Textures;

// RGB albedo, A height. Height is unused until the phase that blends by it; it rides along because a second
// array for one channel would double the samplers for nothing.
//
// The filter is not decoration. Terrain is seen at grazing angles almost exclusively, which is precisely where
// isotropic filtering turns a ground texture into grey mush, and repeat_enable is not optional at all - every
// coordinate here is a world position in tiles and is far outside 0..1.
uniform sampler2DArray albedo_height : source_color, filter_linear_mipmap_anisotropic, repeat_enable;

group_uniforms;

group_uniforms Slots;

// Metres per texture tile, per slot. Larger is coarser.
uniform float slot_uv_scale[8];

// What colour the slot's texture already is, so that the per-vertex tint can be applied as a correction to it
// rather than as a multiply.
//
// A multiply is the obvious thing and it is wrong: palette colours run about 0.5 and a photographic albedo runs
// about 0.45, so albedo * tint lands near 0.22 and the whole world comes out muddy. Dividing by what the
// texture already is makes a material whose texture matches its palette colour a no-op, and makes gold ore a
// correction from granite towards gold rather than a darkening of both.
//
// White is the right default because an unauthored slot is a white placeholder: tint / 1.0 is the tint, so the
// shader reproduces the vertex-colour renderer it replaces exactly. That is the bisect point when something
// looks wrong later - set tint_strength to 1 against white and any remaining difference is not in this file.
uniform vec3 slot_reference_tint[8] : source_color;

// Where each slot's tiling is measured from, in world metres, snapped by the CPU to a whole number of that
// slot's own tiles.
//
// This exists for precision and not for art. Vertices carry absolute world coordinates and the world is 128 km
// across, so a raw world-space UV reaches ~64000 tiles, where a float's spacing is coarser than the difference
// between two adjacent pixels' coordinates. The value would survive that; its DERIVATIVE would not, and the
// derivative is what picks the mip level - so the artefact is not a shifted texture, it is a shimmering one,
// worst close to the camera. Subtracting a nearby origin first keeps the whole calculation small.
//
// Snapped per slot rather than to one shared grid so that no constraint falls on slot_uv_scale. A shared grid
// would have to be a common multiple of every slot's tile size, and an artist typing 3.0 into one of these
// would make the texture jump each time the origin moved.
uniform vec3 slot_uv_origin[8];

// How much a slot's height map is allowed to decide the blend, per slot. Zero blends it linearly by weight
// alone; high values let its crevices fill and its ridges stand out through whatever it borders.
//
// Per slot because materials differ in how much relief they have any business asserting. Gravel and rock should
// push through grass at their edges; snow and sand are drifts that settle into whatever they land on and should
// mostly not.
uniform float slot_height_contrast[8];

group_uniforms;

group_uniforms Blending;

// The band, in height units, over which two materials are both visible.
//
// Small values give a crisp interlocking edge - grass in the cracks, rock proud of it - and large ones fade back
// towards a plain linear blend. Too small and the boundary aliases, because it becomes a step function sampled
// once per pixel.
uniform float height_blend_range : hint_range(0.01, 1.0) = 0.15;

group_uniforms;

group_uniforms Tint;

// How much of the per-vertex tint to apply. Zero shows the textures as authored, which is how you tell a
// texture problem from a palette problem.
uniform float tint_strength : hint_range(0.0, 1.0) = 1.0;

group_uniforms;

group_uniforms Triplanar;

// How sharply the projection favours the dominant axis. Low values smear a texture across a corner; high ones
// make the transition abrupt. Above about four, flat ground is one fetch per slot instead of three.
//
// Worth keeping high also because the normal it is fed is noisy: surface nets derives it from a difference of a
// box-blurred field, so it wobbles at one-voxel features in a way a smooth analytic normal would not.
uniform float triplanar_sharpness : hint_range(1.0, 16.0) = 6.0;

group_uniforms;

varying vec3 v_world_pos;
varying vec3 v_world_normal;

// The eight slot weights, as the two RGBA8 attributes they are packed into.
//
// Forwarded explicitly because CUSTOM0..CUSTOM3 exist only in the vertex stage - unlike COLOR, which is
// available in both and needs none of this.
varying vec4 v_weights_low;
varying vec4 v_weights_high;

struct TerrainSurface {
	vec3 albedo;
	float roughness;

	// Everything below is for the debug shader. It is computed anyway on the way to the albedo, so carrying it
	// out costs registers in a shader that is already register-bound and nothing at all in the shipping one,
	// where it is dead and removed.
	vec3 tint;
	vec3 axis_blend;
	float slope;
	int slot0;
	int slot1;
	float slot0_share;

	// The blended height, which is what the boundary between two materials was actually decided by.
	float height;
};

// THE VERTEX STAGE IS NOT HERE, AND CANNOT BE.
//
// Godot permits a varying to be assigned only inside vertex() itself; a helper called from it is rejected
// outright, and a macro expanded into it takes the preprocessor down with it. So each shader repeats these
// four lines, and they are the only thing the two do not share:
//
//     v_world_pos = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xyz;
//     v_world_normal = normalize(MODEL_NORMAL_MATRIX * NORMAL);
//     v_weights_low = CUSTOM0;
//     v_weights_high = CUSTOM1;
//
// v_world_normal is the geometric normal and is kept separately from NORMAL, because the triplanar projection
// has to be chosen from the surface's actual orientation rather than from whatever a normal map later
// perturbs it into.

// One slot, projected on up to three planes and summed.
//
// The derivatives arrive already computed, in world metres, from the caller. That is a correctness requirement
// and not an optimisation: an implicit texture fetch inside non-uniform control flow has undefined derivatives
// per the spec, and the branches below are the definition of non-uniform. In practice the mip level goes wrong
// exactly along the line where a branch flips - which is the material boundary, the one place anyone is
// looking.
vec4 terrain_sample_slot(int slot, vec3 ddx, vec3 ddy, vec3 blend) {
	float inv_scale = 1.0 / max(slot_uv_scale[slot], 0.0001);

	vec3 p = (v_world_pos - slot_uv_origin[slot]) * inv_scale;
	vec3 px = ddx * inv_scale;
	vec3 py = ddy * inv_scale;

	float layer = float(slot);
	vec4 total = vec4(0.0);

	if (blend.y > TERRAIN_AXIS_EPSILON) {
		total += blend.y * textureGrad(albedo_height, vec3(p.xz, layer), px.xz, py.xz);
	}

	if (blend.x > TERRAIN_AXIS_EPSILON) {
		total += blend.x * textureGrad(albedo_height, vec3(p.zy, layer), px.zy, py.zy);
	}

	if (blend.z > TERRAIN_AXIS_EPSILON) {
		total += blend.z * textureGrad(albedo_height, vec3(p.xy, layer), px.xy, py.xy);
	}

	return total;
}

// The tint is passed in rather than read from COLOR here, because Godot exposes a stage's built-ins only
// inside that stage's own function - a helper called from fragment() cannot see COLOR at all, the same
// restriction that keeps the vertex assignments out of this file.
TerrainSurface terrain_surface(vec3 tint) {
	TerrainSurface surface;

	vec3 normal = normalize(v_world_normal);

	// Zero on flat ground, one on a vertical face.
	surface.slope = 1.0 - abs(normal.y);

	vec3 blend = pow(abs(normal), vec3(triplanar_sharpness));
	blend /= max(blend.x + blend.y + blend.z, 0.0001);
	surface.axis_blend = blend;

	// Hoisted above every branch below, which is the whole point of them - see terrain_sample_slot.
	vec3 ddx = dFdx(v_world_pos);
	vec3 ddy = dFdy(v_world_pos);

	float weights[8];
	weights[0] = v_weights_low.r;
	weights[1] = v_weights_low.g;
	weights[2] = v_weights_low.b;
	weights[3] = v_weights_low.a;
	weights[4] = v_weights_high.r;
	weights[5] = v_weights_high.g;
	weights[6] = v_weights_high.b;
	weights[7] = v_weights_high.a;

	// The two heaviest slots, and only those two get sampled.
	//
	// A weight-culled loop over all eight would be cheaper on a typical pixel and unbounded on the worst one -
	// three axes by eight slots is twenty-four fetches at exactly the corners where the frame is already
	// slowest. This is a fixed cost with no texture fetch inside data-dependent control flow, and the slots it
	// discards are ones a height blend would have crushed to nothing anyway.
	int slot0 = 0;
	int slot1 = 0;
	float best0 = -1.0;
	float best1 = -1.0;

	for (int i = 0; i < 8; i++) {
		float w = weights[i];

		if (w > best0) {
			slot1 = slot0;
			best1 = best0;
			slot0 = i;
			best0 = w;
		} else if (w > best1) {
			slot1 = i;
			best1 = w;
		}
	}

	surface.slot0 = slot0;
	surface.slot1 = slot1;

	// Renormalised over the pair. The runner-up fades out rather than being cut off, so the frame where two
	// slots swap rank is not the frame the ground changes material.
	float pair = max(best0 + best1, 0.0001);
	float share0 = best0 / pair;
	float share1 = 1.0 - share0;

	vec4 sample0 = terrain_sample_slot(slot0, ddx, ddy, blend);
	vec4 sample1 = vec4(0.0);

	bool has_second = best1 > TERRAIN_WEIGHT_EPSILON;

	if (has_second) {
		sample1 = terrain_sample_slot(slot1, ddx, ddy, blend);
	}

	// HEIGHT BLENDING.
	//
	// A linear cross-fade between two ground materials always looks like what it is: a fade. What real ground
	// does at a boundary is interlock - grass survives in the cracks between stones long after it has lost the
	// tops of them - so the mixing is decided by each material's own height map rather than by distance.
	//
	// Both heights are offset by the slot's weight, so the vertex data still says which material is mostly here
	// and the height map only decides where within the transition each one wins. Then everything within
	// height_blend_range of the taller is kept, which is what makes the edge crisp without making it a step.
	float height0 = sample0.a * slot_height_contrast[slot0] + share0;
	float height1 = sample1.a * slot_height_contrast[slot1] + share1;

	float cutoff = max(height0, height1) - height_blend_range;

	float above0 = max(height0 - cutoff, 0.0);
	float above1 = has_second ? max(height1 - cutoff, 0.0) : 0.0;

	float above = max(above0 + above1, 0.0001);
	share0 = above0 / above;
	share1 = above1 / above;

	surface.slot0_share = share0;
	surface.height = share0 * sample0.a + share1 * sample1.a;

	vec4 sampled = share0 * sample0 + share1 * sample1;

	// The reference tint follows the same blend as the texture, so a pixel halfway between grass and rock is
	// corrected against a texture that is halfway between them too - which is what it was actually sampled from.
	vec3 reference = share0 * slot_reference_tint[slot0] + share1 * slot_reference_tint[slot1];

	surface.tint = tint;

	vec3 correction = tint / max(reference, vec3(0.0001));

	surface.albedo = sampled.rgb * mix(vec3(1.0), correction, tint_strength);

	// Rock, soil and grass, none of which are polished. Stated here rather than left to the material because a
	// large surface with any specular on it mirrors the sky straight back at the camera, and the result is
	// indistinguishable on screen from the material having failed to apply at all.
	surface.roughness = 1.0;

	return surface;
}

