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
//     0 NEUTRAL   1 GRASS   2 DRY_GRASS   3 SAND   4 SOIL   5 ROCK   6 SNOW   7 WETLAND
//
// Slot 7 was held in reserve for the first material that genuinely needed its own texture rather than a tint
// over somebody else's. That material is MUD: a bog on the soil layer was a ploughed field in a darker brown,
// because wetness is not a grain and no tint supplies one. The reserve is now spent - there is no ninth slot
// short of doubling the vertex format.

// The slot count is written as a literal 8 throughout rather than as a name.
//
// Not a style choice - Godot's shader preprocessor only runs for a shader loaded from a path, and it gives up
// on the whole file if it dislikes any directive, silently handing the raw text with its '#'s to the tokenizer.
// The failure is reported at the '#' rather than at the cause, so preprocessor use here is worth keeping to the
// one directive that earns it, which is #include. See TERRAIN_SLOTS in BlockAppearance for the authority on
// this number; anything that changes it changes the vertex format anyway.

// Slot ordinals the shader itself has to name. Everything else it treats as opaque, which is the point of the
// encoding - but slope has to know what rock is, and weather has to know what snow is.
const int TERRAIN_SLOT_ROCK = 5;
const int TERRAIN_SLOT_SNOW = 6;

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

// RG tangent normal, B roughness, A ambient occlusion. Linear, not source_color - none of these four channels
// is a colour, and putting them through an sRGB curve would bend every one of them.
uniform sampler2DArray normal_rough_ao : filter_linear_mipmap_anisotropic, repeat_enable;

group_uniforms;

group_uniforms Surface;

// How much of the normal map to apply. Zero is the geometric surface alone, which is the comparison worth
// having when lighting looks wrong: if it looks the same at zero and at one, the normals never arrived.
uniform float normal_strength : hint_range(0.0, 2.0) = 1.0;

// How much the texture's ambient occlusion darkens it.
uniform float ao_strength : hint_range(0.0, 1.0) = 1.0;

group_uniforms;

group_uniforms Slots;

// Metres per texture tile, per slot. Larger is coarser.
uniform float slot_uv_scale[16];

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
// Already linear, so no source_color hint: TerrainSlotTextures averages the decoded texture rather than its
// storage bytes, which is the space the division below happens in. Tagging it source_color would decode it a
// second time and brighten every material by the same wrong amount.
uniform vec3 slot_reference_tint[16];

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
uniform vec3 slot_uv_origin[16];

// How much a slot's height map is allowed to decide the blend, per slot. Zero blends it linearly by weight
// alone; high values let its crevices fill and its ridges stand out through whatever it borders.
//
// Per slot because materials differ in how much relief they have any business asserting. Gravel and rock should
// push through grass at their edges; snow and sand are drifts that settle into whatever they land on and should
// mostly not.
uniform float slot_height_contrast[16];

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

// Whether the palette's colours are read as sRGB or as linear.
//
// Off matches what the vertex-colour renderer did, and is the default so that turning the shader on did not
// silently restyle the world. It is almost certainly not what the palette author meant, though: BlockAppearance
// picks values like GRANITE 0.60/0.56/0.55 as "what grey granite looks like", and rendering them as linear puts
// them on screen around 0.80 - which is why bare rock reads closer to white than to stone. Turning this on
// makes the world match the numbers in that file. It is a change to how everything looks, so it is a decision
// rather than a fix, and this is here to make it a one-click one.
uniform bool tint_is_srgb = false;

group_uniforms;

group_uniforms Triplanar;

// How sharply the projection favours the dominant axis. Low values smear a texture across a corner; high ones
// make the transition abrupt. Above about four, flat ground is one fetch per slot instead of three.
//
// Worth keeping high also because the normal it is fed is noisy: surface nets derives it from a difference of a
// box-blurred field, so it wobbles at one-voxel features in a way a smooth analytic normal would not.
uniform float triplanar_sharpness : hint_range(1.0, 16.0) = 6.0;

group_uniforms;

group_uniforms Slope;

// Where loose cover starts giving way to bare rock, and where it has entirely gone. Slope is zero on flat
// ground and one on a vertical face.
uniform float cliff_start : hint_range(0.0, 1.0) = 0.35;
uniform float cliff_end : hint_range(0.0, 1.0) = 0.62;

group_uniforms;

group_uniforms Weather;

// Driven by WeatherState from the server's WeatherSMSG, as globals rather than material parameters so that
// water, foliage and anything else added later reads the same numbers rather than its own copy.
global uniform float weather_wetness;
global uniform float weather_snow;
global uniform float weather_temperature;

// How dark wet ground goes. Real ground darkens because water fills the pores and light stops scattering back
// out; 0.72 is about right for soil and generous for rock, which matters less than it having one value.
uniform float wetness_darkening : hint_range(0.3, 1.0) = 0.72;

// How smooth standing water is. Terrain is otherwise fully rough, so this is the only specular in the scene.
uniform float wetness_roughness : hint_range(0.0, 1.0) = 0.12;

// The colour fresh snow is corrected to, matching the palette's own SNOW so that weather snow and the snow the
// generator placed do not read as two different materials.
uniform vec3 snow_colour : source_color = vec3(0.92, 0.94, 0.96);

// Above this, snow does not lie however hard it falls.
uniform float snow_melt_celsius : hint_range(-20.0, 20.0) = 1.0;

group_uniforms;

varying vec3 v_world_pos;
varying vec3 v_world_normal;

// The eight slot weights, as the two RGBA8 attributes they are packed into.
//
// Forwarded explicitly because CUSTOM0..CUSTOM3 exist only in the vertex stage - unlike COLOR, which is
// available in both and needs none of this.
varying vec4 v_weights_low;
varying vec4 v_weights_high;
varying vec4 v_weights_low2;
varying vec4 v_weights_high2;

struct TerrainSurface {
	vec3 albedo;
	float roughness;

	/// The shaded normal, in world space. The caller converts it, because which space it wants depends on where
	/// it is putting it.
	vec3 world_normal;

	float ao;

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

	// How much lying snow and standing water ended up on this pixel.
	float snow;
	float wetness;
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
//     v_weights_low2 = CUSTOM2;
//     v_weights_high2 = CUSTOM3;
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
struct SlotSample {
	vec3 albedo;
	float height;

	// World space already: the reorientation below has to happen per projection axis, so it cannot be deferred
	// until after the two slots are blended.
	vec3 normal;

	float roughness;
	float ao;
};

SlotSample terrain_sample_slot(int slot, vec3 geometric, vec3 ddx, vec3 ddy, vec3 blend) {
	float inv_scale = 1.0 / max(slot_uv_scale[slot], 0.0001);

	vec3 p = (v_world_pos - slot_uv_origin[slot]) * inv_scale;
	vec3 px = ddx * inv_scale;
	vec3 py = ddy * inv_scale;

	float layer = float(slot);

	SlotSample result;
	result.albedo = vec3(0.0);
	result.height = 0.0;
	result.roughness = 0.0;
	result.ao = 0.0;

	vec3 normal = vec3(0.0);

	// WHITEOUT BLENDING.
	//
	// Three projections each give a tangent normal in their own plane, and they cannot simply be averaged -
	// they do not share a tangent frame. Whiteout adds each one's xy to the geometric normal's two matching
	// components and keeps the sign of the third, which reorients it into world space without needing the mesh
	// to carry tangents. Which is just as well, because this mesh has none: surface nets writes positions,
	// normals, a tint and the slot weights, so NORMAL_MAP is not available to us at any price.
	if (blend.y > TERRAIN_AXIS_EPSILON) {
		vec4 colour = textureGrad(albedo_height, vec3(p.xz, layer), px.xz, py.xz);
		vec4 maps = textureGrad(normal_rough_ao, vec3(p.xz, layer), px.xz, py.xz);

		result.albedo += blend.y * colour.rgb;
		result.height += blend.y * colour.a;
		result.roughness += blend.y * maps.b;
		result.ao += blend.y * maps.a;

		vec2 tangent = (maps.rg * 2.0 - 1.0) * normal_strength;
		normal += blend.y * vec3(tangent.x + geometric.x, geometric.y, tangent.y + geometric.z);
	}

	if (blend.x > TERRAIN_AXIS_EPSILON) {
		vec4 colour = textureGrad(albedo_height, vec3(p.zy, layer), px.zy, py.zy);
		vec4 maps = textureGrad(normal_rough_ao, vec3(p.zy, layer), px.zy, py.zy);

		result.albedo += blend.x * colour.rgb;
		result.height += blend.x * colour.a;
		result.roughness += blend.x * maps.b;
		result.ao += blend.x * maps.a;

		vec2 tangent = (maps.rg * 2.0 - 1.0) * normal_strength;
		normal += blend.x * vec3(geometric.x, tangent.y + geometric.y, tangent.x + geometric.z);
	}

	if (blend.z > TERRAIN_AXIS_EPSILON) {
		vec4 colour = textureGrad(albedo_height, vec3(p.xy, layer), px.xy, py.xy);
		vec4 maps = textureGrad(normal_rough_ao, vec3(p.xy, layer), px.xy, py.xy);

		result.albedo += blend.z * colour.rgb;
		result.height += blend.z * colour.a;
		result.roughness += blend.z * maps.b;
		result.ao += blend.z * maps.a;

		vec2 tangent = (maps.rg * 2.0 - 1.0) * normal_strength;
		normal += blend.z * vec3(tangent.x + geometric.x, tangent.y + geometric.y, geometric.z);
	}

	result.normal = normalize(normal);

	return result;
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

	float weights[16];
	weights[0] = v_weights_low.r;
	weights[1] = v_weights_low.g;
	weights[2] = v_weights_low.b;
	weights[3] = v_weights_low.a;
	weights[4] = v_weights_high.r;
	weights[5] = v_weights_high.g;
	weights[6] = v_weights_high.b;
	weights[7] = v_weights_high.a;
	weights[8] = v_weights_low2.r;
	weights[9] = v_weights_low2.g;
	weights[10] = v_weights_low2.b;
	weights[11] = v_weights_low2.a;
	weights[12] = v_weights_high2.r;
	weights[13] = v_weights_high2.g;
	weights[14] = v_weights_high2.b;
	weights[15] = v_weights_high2.a;

	// SLOPE REWEIGHTS THE SPLAT RATHER THAN OVERLAYING A LAYER.
	//
	// The generator already caps steep ground with bare rock, so a cliff layer composited on top would apply
	// twice where it is right and still leave grass clinging to an eighty-degree wall where the generator's own
	// threshold was coarser than a pixel. Moving weight instead composes with what the data already says: the
	// cliff face that was rock stays rock, and the one that was grass stops being it.
	//
	// Only loose cover moves. Rock is already where it is going, and snow that the generator placed is a
	// snowfield rather than something gravity pulls off a wall.
	float cliff = smoothstep(cliff_start, cliff_end, surface.slope);

	if (cliff > 0.0) {
		float shed = 0.0;

		for (int i = 1; i <= 4; i++) {
			float loose = weights[i] * cliff;

			weights[i] -= loose;
			shed += loose;
		}

		weights[TERRAIN_SLOT_ROCK] += shed;
	}

	// The two heaviest slots, and only those two get sampled.
	//
	// A weight-culled loop over all sixteen would be cheaper on a typical pixel and unbounded on the worst one -
	// three axes by sixteen slots is forty-eight fetches at exactly the corners where the frame is already
	// slowest. This is a fixed cost with no texture fetch inside data-dependent control flow, and the slots it
	// discards are ones a height blend would have crushed to nothing anyway.
	//
	// This is also why widening the palette from eight slots to sixteen cost no per-pixel texture bandwidth at
	// all: the search below grew by eight integer compares and the number of fetches did not move.
	int slot0 = 0;
	int slot1 = 0;
	float best0 = -1.0;
	float best1 = -1.0;

	for (int i = 0; i < 16; i++) {
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

	SlotSample sample0 = terrain_sample_slot(slot0, normal, ddx, ddy, blend);

	SlotSample sample1;
	sample1.albedo = vec3(0.0);
	sample1.height = 0.0;
	sample1.normal = normal;
	sample1.roughness = 1.0;
	sample1.ao = 1.0;

	bool has_second = best1 > TERRAIN_WEIGHT_EPSILON;

	if (has_second) {
		sample1 = terrain_sample_slot(slot1, normal, ddx, ddy, blend);
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
	float height0 = sample0.height * slot_height_contrast[slot0] + share0;
	float height1 = sample1.height * slot_height_contrast[slot1] + share1;

	float cutoff = max(height0, height1) - height_blend_range;

	float above0 = max(height0 - cutoff, 0.0);
	float above1 = has_second ? max(height1 - cutoff, 0.0) : 0.0;

	float above = max(above0 + above1, 0.0001);
	share0 = above0 / above;
	share1 = above1 / above;

	surface.slot0_share = share0;
	surface.height = share0 * sample0.height + share1 * sample1.height;

	// The reference tint follows the same blend as the texture, so a pixel halfway between grass and rock is
	// corrected against a texture that is halfway between them too - which is what it was actually sampled from.
	vec3 reference = share0 * slot_reference_tint[slot0] + share1 * slot_reference_tint[slot1];

	surface.tint = tint;

	vec3 palette = tint_is_srgb
		? mix(tint / 12.92, pow((tint + vec3(0.055)) / 1.055, vec3(2.4)), step(vec3(0.04045), tint))
		: tint;

	vec3 correction = palette / max(reference, vec3(0.0001));
	vec3 albedo = share0 * sample0.albedo + share1 * sample1.albedo;

	albedo *= mix(vec3(1.0), correction, tint_strength);

	// Normals from the two slots are already in world space, so blending them is a plain interpolation. It is
	// only within one slot that the three projections needed reorienting first.
	vec3 world_normal = normalize(share0 * sample0.normal + share1 * sample1.normal);

	float ao = mix(1.0, share0 * sample0.ao + share1 * sample1.ao, ao_strength);

	// Rock, soil and grass, none of which are polished, so an absent roughness map means fully rough rather
	// than the zero it would otherwise read as. The material's own map narrows it from there.
	float roughness = clamp(share0 * sample0.roughness + share1 * sample1.roughness, 0.04, 1.0);

	// SNOW: a layer on top, and deliberately not the same thing as slot 6.
	//
	// Slot 6 is snowpack the generator placed - it is what the ground there is made of, and it is there in
	// summer. This is weather, it arrives and leaves without the chunk being remeshed, and it lies on whatever
	// is underneath rather than replacing it. Conflating the two turns the whole world white the first time it
	// snows anywhere.
	//
	// It settles on what faces up, melts above a temperature, and fills hollows first - which is what the
	// height channel is doing in the mask.
	float cold = smoothstep(snow_melt_celsius + 3.0, snow_melt_celsius, weather_temperature);
	float settled = smoothstep(cliff_end, cliff_start, surface.slope) * max(world_normal.y, 0.0);
	float snow = clamp(weather_snow, 0.0, 1.0) * cold * settled;

	// Deepening snow reaches the ridges last, so the mask leads in the crevices and catches up at full depth.
	snow = clamp(snow * (1.0 + (1.0 - surface.height)) - (1.0 - snow) * surface.height * 0.5, 0.0, 1.0);

	if (snow > TERRAIN_WEIGHT_EPSILON) {
		SlotSample fresh = terrain_sample_slot(TERRAIN_SLOT_SNOW, normal, ddx, ddy, blend);

		// Corrected the same way the ground was, so weather snow and generator snow land on the same colour.
		vec3 fresh_albedo = fresh.albedo * (snow_colour / max(slot_reference_tint[TERRAIN_SLOT_SNOW], vec3(0.0001)));

		albedo = mix(albedo, fresh_albedo, snow);
		world_normal = normalize(mix(world_normal, fresh.normal, snow));
		roughness = mix(roughness, fresh.roughness, snow);

		// Snow buries small occlusion along with everything else.
		ao = mix(ao, 1.0, snow);
	}

	// WETNESS: no extra samples, only different constants.
	//
	// Wet ground is darker because water fills its pores and light stops scattering back out, and smoother
	// because the water surface is what is being seen. Standing water collects in the low parts of the height
	// map, so the roughness drop leads there - which is what makes rain read as puddles rather than as varnish.
	//
	// Snow that is lying takes precedence: it is the surface, and it is not wet.
	float wet = clamp(weather_wetness, 0.0, 1.0) * (1.0 - snow);

	if (wet > TERRAIN_WEIGHT_EPSILON) {
		float pooled = wet * smoothstep(0.55, 0.15, surface.height) * max(world_normal.y, 0.0);

		albedo *= mix(1.0, wetness_darkening, wet);
		roughness = mix(roughness, wetness_roughness, pooled);
	}

	surface.albedo = albedo;
	surface.world_normal = world_normal;
	surface.roughness = roughness;
	surface.ao = ao;
	surface.snow = snow;
	surface.wetness = wet;

	return surface;
}

