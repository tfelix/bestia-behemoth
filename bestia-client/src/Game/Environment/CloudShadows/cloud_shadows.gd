extends Node3D

## Drifts cloud shadows across the landscape at wind speed.
##
## A grid of [Decal]s projecting a seamless noise mask straight down, wrapping around the camera as it moves
## and scrolling with the weather's own wind. Decals are blended into albedo by the renderer after each
## material has run, so terrain, props, water and entities all darken together and no shader has to know this
## exists - which is the requirement that chose the technique.
##
## Two alternatives were tried on paper first and are worth recording, because both look obvious:
##
## [b]A light cookie on the sun.[/b] [code]light_projector[/code] is Omni and Spot only in Godot 4;
## [DirectionalLight3D] has no such property. There is nothing to hang a cloud texture on.
##
## [b]A shadow-casting cloud plane above the world.[/b] Physically the real thing, and it rides the sun's
## PSSM splits: the pattern is huge and soft, the splits sample it at four different densities, and the seams
## between them fall across it. It also smears without limit once the sun elevation hits its floor.
##
## [b]Why the tiles overlap instead of abutting.[/b] A decal's texture is copied into the renderer's decal
## atlas and sampled with clamping, so the outer half texel of every decal blends toward the transparent
## surround of its atlas entry: a tile that stops exactly where its neighbour starts loses up to half of its
## shadow along the join. On screen that is a bright line a texel wide, running the length of the tile and
## drifting with the wind - the first thing the eye finds in an otherwise soft mask. Overlapping the tiles is
## not the fix by itself, because two decals over one fragment each mix toward the shadow in turn, so an
## overlap is a dark line where an abutting edge was a bright one. Both together work: the tiles overlap by
## [member blend_metres] and the mask's alpha carries a window that reaches zero at its edge, so neighbours
## crossfade. Measured against a deliberately featureless mask, that took the join from an eight percent
## bright spike down to under half a percent, which is less than the render varies by anyway.

## Metres across per cloud tile. Three of these cover the camera in each axis, so the grid reaches 1.5 tiles
## in every direction - comfortably past [code]fog_depth_end[/code], where nothing is visible anyway.
##
## This is the period of the mask rather than the size of a decal: each decal is [member blend_metres] wider,
## so that neighbours can overlap.
@export_range(64, 1024, 32) var tile_metres: float = 256.0

## How far neighbouring tiles overlap and crossfade, in metres.
##
## Generous rather than tight, and that is the point. The crossfade is not exact - two decals over one
## fragment compose as two mixes rather than one, which leaves the middle of a join a couple of percent
## lighter than the middle of a tile. Spread over tens of metres that is indistinguishable from the cloud
## itself; squeezed into one metre the same error reads as a line. What it costs is that the overlap is drawn
## by two decals rather than one.
@export_range(4, 128, 4) var blend_metres: float = 32.0

## How deep each decal projects, in metres, centred on the camera.
##
## Has to span the terrain relief underneath it or shadows stop at the height the camera happens to be at.
## Generous rather than exact: the cost of a decal is its screen area, not its depth.
@export_range(64, 1024, 32) var depth_metres: float = 400.0

## How much darker the ground goes under the thickest part of a cloud, at full strength.
@export_range(0, 1, 0.01) var max_darkness: float = 0.45

## What a shadowed patch of ground would be tinted toward.
##
## Not black, and not neutral. Ground in cloud shadow is still lit - by the sky, which is blue - so the
## shadow is a colour shift as much as a darkening. Black patches read as holes in the terrain.
##
## [b]Inert as it stands, and left here rather than deleted.[/b] The renderer multiplies this into the
## sampled mask before mixing it into albedo, and the mask is black with only its alpha carrying the cloud -
## so the product is black whatever this says, and shadows mix toward black. Proven by rendering a decal with
## a pure red modulate: the shadowed ground came back with its red channel untouched at 0.69, and only rose
## to 0.94 once the mask itself carried white. Making it real is one line - luminance 255 in
## [method _bake_mask] - but it lightens every shadow in the same breath, because mixing 45% toward a mid
## blue-grey is nowhere near mixing 45% toward black, so [member max_darkness] wants re-tuning with it.
@export var shadow_colour: Color = Color(0.30, 0.35, 0.46)

## How fast the clouds travel relative to the wind at head height, which is what the server reports.
##
## Well below 1, which is not the physics - wind aloft is faster than wind at the ground, not slower. It is
## the frame. The camera sees forty-odd metres of ground, so a shadow moving at anything like the reported
## wind crosses the whole view in a second or two and reads as a strobing patch rather than as weather. What
## the number is really tuned against is how long a patch stays on screen, and that wants tens of seconds.
##
## Set so that a gale looks like a gale rather than so that a calm day looks right: wind is the one input
## here that already varies hugely by itself, from a couple of metres per second to twenty-odd, so the
## multiplier has to leave headroom at the top rather than being tuned on the average and blowing out.
@export_range(0.05, 8, 0.05) var wind_multiplier: float = 0.3

## How high the cloud deck sits, in metres. Only used to place the shadows, never drawn.
@export_range(0, 1000, 10) var cloud_altitude: float = 220.0

## How many clouds across a tile. Lower means fewer, larger ones.
##
## Tuned against what the camera can actually see, which is the part that is easy to get wrong: it orbits
## eight to thirty-six metres out and looks down, so the ground in frame is forty-odd metres across while a
## tile is two hundred and fifty-six. At two clouds per tile each one is over a hundred metres wide, and the
## whole visible window sits inside a single patch - which renders as the ground slowly changing brightness
## rather than as shadows crossing it.
@export_range(0.5, 16, 0.1) var cloud_scale: float = 5.0

@export_range(64, 1024, 64) var texture_size: int = 512

## Below this daylight the shadows are gone. There is nothing to cast them.
@export_range(0, 1, 0.01) var daylight_floor: float = 0.06

const _GRID := 3

## How far cover has to move before the mask is worth rebuilding. The bake runs on a worker thread, so this
## is cheap - but not free, and cover drifts continuously.
const _COVERAGE_BAND := 0.08

## Half the width of the ramp from clear sky to solid cloud, in noise units - how soft the threshold that
## makes the mask is. Wide enough that cloud edges are soft, narrow enough that cover still means something.
const _EDGE := 0.22

var _weather: Node = null
var _clock: Node = null

var _decals: Array[Decal] = []

## The noise field: one tile, one byte per texel, wrapping at [member texture_size]. Generated once - cover
## moves the threshold, not the field.
var _field := PackedByteArray()

## The crossfade window, one weight per mask texel, used on both axes. See [method _build_window].
var _window := PackedFloat32Array()

## What every decal samples. Rebuilt in place with [method ImageTexture.set_image], which reaches the decal
## atlas where [method ImageTexture.update] silently does not.
var _mask := ImageTexture.new()

## Border texels on each side of the mask: half the overlap, rounded to whole texels.
var _pad := 0

## Metres across per decal - a tile plus both borders. The grid is still spaced by [member tile_metres].
var _span := 0.0

var _bake_task := -1

## Where the cloud field has drifted to, in metres. Grows without bound on purpose - it is only ever used
## modulo [member tile_metres], and a float carries far more session than anyone will play.
var _drift := Vector2.ZERO

## The sun-parallax offset, smoothed. See [method _sun_offset].
var _parallax := Vector2.ZERO

## Whether [member _parallax] has ever held a real offset, as opposed to its starting zero.
##
## The smoothing below is for the sun [i]moving[/i], and it is slow on purpose: the sun only steps every
## [member update_delay_seconds], and near sunrise the parallax term grows fast enough that an unsmoothed
## one would shift the whole shadow field sideways once every ten seconds.
##
## None of that applies to the first offset, because there is no previous sun to come from. Lerping to it from
## zero swept the field across up to four tiles - a kilometre - in the two seconds after the shadows first
## became visible, which reads as clouds tearing overhead and then settling.
var _parallax_seeded := false

var _built_coverage := -1.0


func _ready() -> void:
	_weather = ConnectionManager.weather
	if _weather == null:
		push_warning("[clouds] no WeatherState; cloud shadows will not run.")

	_clock = ConnectionManager.world_clock

	_measure()
	_build_window()
	_build_decals()

	visible = false


func _process(delta: float) -> void:
	var strength := _strength()

	# Hidden rather than merely transparent. A decal at zero mix still costs its screen area in the decal
	# pass, and a clear day is the common case.
	visible = strength > 0.005
	if not visible:
		return

	var camera := get_viewport().get_camera_3d()
	if camera == null:
		visible = false
		return

	var overcast: float = _weather.Overcast
	_ensure_coverage(overcast)

	# The first bake lands a frame or two after the first cloudy frame. Before it does there is nothing to
	# project - nothing that would still cost its screen area in the decal pass.
	if _mask.get_width() == 0:
		visible = false
		return

	var wind: Vector3 = _weather.Wind
	_drift += Vector2(wind.x, wind.z) * wind_multiplier * delta

	# Smoothed because the sun only moves every update_delay_seconds, and an unsmoothed parallax would step
	# the whole shadow field sideways once every ten seconds near sunrise, where the term grows fastest. Taken
	# whole the first time, though - see _parallax_seeded.
	var parallax_target := _sun_offset()
	if _parallax_seeded:
		_parallax = _parallax.lerp(parallax_target, 1.0 - exp(-delta / 2.0))
	else:
		_parallax_seeded = true
		_parallax = parallax_target

	_place(camera.global_position, _drift + _parallax, strength)


## How hard the shadows should read: the weather's own curve, gated on there being a sun to cast them.
func _strength() -> float:
	if _weather == null:
		return 0.0

	# Landed in typed locals first, and so is every other read of a C# property in these scripts: they cross
	# the language boundary as Variant, and letting one flow straight into arithmetic hides its type from the
	# analyser for the rest of the expression.
	var daylight := 1.0
	if _clock != null and _clock.IsAnchored():
		var lit: float = _clock.Daylight
		daylight = lit

	var shadow: float = _weather.ShadowStrength

	return shadow * smoothstep(daylight_floor, 0.4, daylight)


## Where a cloud directly overhead throws its shadow, as a world-space XZ offset in metres.
##
## A shadow lands [code]altitude / tan(elevation)[/code] from the point below the cloud, along the direction
## the light travels. Doing it this way keeps every decal a cheap axis-aligned top-down box while the shadows
## still sit where a slanted projection would put them - which matters most at exactly the hours the offset
## is largest and the error would be a hundred metres.
func _sun_offset() -> Vector2:
	var sun := get_parent().get_node_or_null(^"Sun") as DirectionalLight3D
	if sun == null or not sun.visible:
		return Vector2.ZERO

	# A DirectionalLight3D shines along its own -Z.
	var travel := -sun.global_transform.basis.z
	var elevation := asin(clampf(-travel.y, -1.0, 1.0))
	if elevation <= 0.01:
		return Vector2.ZERO

	var horizontal := Vector2(travel.x, travel.z)
	if horizontal.length_squared() < 0.000001:
		return Vector2.ZERO

	var reach := cloud_altitude / tan(elevation)

	# Capped for its own sake rather than the renderer's: the offset is unbounded as the sun approaches the
	# horizon, and past a few tiles it is only choosing which identical repeat of the pattern to show.
	return horizontal.normalized() * minf(reach, tile_metres * 4.0)


## Wraps the grid around the camera and applies the current strength.
##
## The grid is spaced by [member tile_metres] however wide the decals themselves are, and aligned so that each
## tile starts exactly where the cloud field repeats - which is what lets it teleport by a whole tile without
## anything showing: the mask has period [member tile_metres], so the pattern either side of the jump is
## identical.
func _place(camera_position: Vector3, offset: Vector2, strength: float) -> void:
	var half := tile_metres * 0.5
	var mix := strength * max_darkness

	# Valid centres are offset + half + k * tile. Rounding picks the k nearest the camera, so the camera is
	# never more than half a tile from the middle decal.
	var origin := Vector2(
		offset.x + half + round((camera_position.x - offset.x - half) / tile_metres) * tile_metres,
		offset.y + half + round((camera_position.z - offset.y - half) / tile_metres) * tile_metres)

	var index := 0
	for row in range(-1, _GRID - 1):
		for column in range(-1, _GRID - 1):
			var decal := _decals[index]
			index += 1

			decal.global_position = Vector3(
				origin.x + float(column) * tile_metres,
				camera_position.y,
				origin.y + float(row) * tile_metres)
			decal.albedo_mix = mix


## Turns [member blend_metres] into whole texels, because the overlap and the window have to agree to the
## texel: half a texel of disagreement is half a texel of the join not summing to one, which is the line all
## of this exists to remove.
func _measure() -> void:
	var texel := tile_metres / float(texture_size)

	# At least a texel of border, and at most a quarter tile: each window ramp is 2 * pad wide, and the two
	# of them must not meet in the middle.
	_pad = clampi(int(round(blend_metres * 0.5 / texel)), 1, texture_size / 4)
	_span = tile_metres + float(2 * _pad) * texel


## Builds the crossfade window: one weight per mask texel, applied along both axes.
##
## Ramps up over the first [code]2 * _pad[/code] texels, down over the last, and is 1 in between. The pair
## that has to agree is a texel [code]i[/code] of one tile and the same patch of ground in the next, which is
## texel [code]i + texture_size[/code] of that neighbour - and [method @GlobalScope.smoothstep] is symmetric
## about its middle, so those two weights sum to exactly 1. Both axes use the same window and the weight is
## their product, so an overlapping corner, where four decals meet, sums to 1 as well.
func _build_window() -> void:
	var total := texture_size + 2 * _pad
	var ramp := float(2 * _pad)

	_window.resize(total)
	for i in total:
		var rising := smoothstep(0.0, 1.0, clampf((float(i) + 0.5) / ramp, 0.0, 1.0))
		var falling := smoothstep(0.0, 1.0, clampf((float(total) - 0.5 - float(i)) / ramp, 0.0, 1.0))
		_window[i] = rising * falling


## Rebuilds the mask when the sky has changed enough to be worth it.
##
## Cover moves the threshold, which grows the patches; strength separately sets how dark they go. Both are
## needed, and the first is the one that is easy to skip: a fixed pattern fading up and down is the tell that
## gives a cheap effect away, because real cloud shadow gets larger before it gets darker.
func _ensure_coverage(coverage: float) -> void:
	if absf(coverage - _built_coverage) < _COVERAGE_BAND:
		return

	# One bake at a time, and the coverage is deliberately not recorded when one is already running: the next
	# frame asks again, rather than the sky quietly settling wherever the last finished bake left it.
	if _bake_task != -1 and not WorkerThreadPool.is_task_completed(_bake_task):
		return

	_built_coverage = coverage
	_bake_task = WorkerThreadPool.add_task(_bake.bind(coverage), true, "cloud shadow mask")


## Runs on a worker thread. A third of a million texels of threshold and window is a couple of frames' work,
## and cover crosses [constant _COVERAGE_BAND] several times during a single weather change - so on the main
## thread it would be several dropped frames spread over the ten seconds the player spends watching the sky.
func _bake(coverage: float) -> void:
	if _field.is_empty():
		_build_field()

	_publish.call_deferred(_bake_mask(coverage))


func _publish(image: Image) -> void:
	if _bake_task != -1:
		# The documented way to release a task rather than a real wait: the task is what deferred this call.
		WorkerThreadPool.wait_for_task_completion(_bake_task)
		_bake_task = -1

	_mask.set_image(image)


## Generates the noise field: one tile, seamless, one byte per texel.
##
## Once, not per bake. Cover is a threshold on this field and thresholding a byte is arithmetic, while four
## octaves of noise over a quarter of a million texels is the expensive half of the old rebuild.
func _build_field() -> void:
	var noise := FastNoiseLite.new()
	noise.noise_type = FastNoiseLite.TYPE_SIMPLEX_SMOOTH
	noise.fractal_type = FastNoiseLite.FRACTAL_FBM
	noise.fractal_octaves = 4
	noise.fractal_gain = 0.45
	noise.frequency = cloud_scale / float(texture_size)

	var image := noise.get_seamless_image(texture_size, texture_size)

	# One byte per texel is what the bake indexes. Converting is cheaper than trusting the default.
	if image.get_format() != Image.FORMAT_L8:
		image.convert(Image.FORMAT_L8)

	_field = image.get_data()


## Bakes the mask the decals sample.
##
## The field is one tile; the mask is that field wrapped into an image [code]2 * _pad[/code] texels wider, so
## its border repeats what the neighbouring tile begins with, with the crossfade window multiplied into the
## alpha. Cover enters as the threshold, which falls as cover rises - so more of the field clears the bar and
## the patches grow.
##
## [b]LA8[/b], where a [Gradient] on a [NoiseTexture2D] used to do this: two bytes a texel, of which only the
## alpha is written. Not an optimisation - the alpha has to be the threshold times the window, and a gradient
## only ever sees the noise value, never where in the tile it sits.
func _bake_mask(coverage: float) -> Image:
	var size := texture_size
	var total := size + 2 * _pad

	# Falls as cover rises, so more of the noise field clears the bar and the patches grow.
	var threshold := lerpf(0.85, 0.15, clampf(coverage, 0.0, 1.0))
	var clear_below := (threshold - _EDGE) * 255.0
	var per_unit := 255.0 / (2.0 * _EDGE * 255.0)

	var data := PackedByteArray()
	data.resize(total * total * 2)

	# The luminance byte stays at the zero the resize left it, so the mask is black and only its alpha carries
	# the cloud. See [member shadow_colour] for what that costs.
	var out := 1
	for y in total:
		var row := posmod(y - _pad, size) * size
		var weight: float = _window[y] * per_unit
		for x in total:
			var alpha := (float(_field[row + posmod(x - _pad, size)]) - clear_below) * weight * _window[x]
			data[out] = clampi(int(alpha), 0, 255)
			out += 2

	var image := Image.create_from_data(total, total, false, Image.FORMAT_LA8, data)

	# Mipmaps for the far end of the grid, which reaches well past the fog. The near tiles are magnified and
	# never sample below the top level.
	image.generate_mipmaps()

	return image


func _build_decals() -> void:
	# Built here rather than authored into the scene because there are nine of them and they differ only by
	# where they are, which is decided per frame anyway.
	for i in _GRID * _GRID:
		var decal := Decal.new()
		decal.name = "CloudShadow%d" % i
		decal.size = Vector3(_span, depth_metres, _span)
		decal.texture_albedo = _mask
		decal.modulate = shadow_colour

		# Zero rather than the default, in both directions: the fade is along the projection axis, and any of
		# it would make the shadows depend on how far the ground happens to be below the camera.
		decal.upper_fade = 0.0
		decal.lower_fade = 0.0

		# Cloud shadow falls on what faces the sky. Without this it also paints down cliff faces and cave
		# ceilings, where a shadow from above has no business being.
		decal.normal_fade = 0.6

		decal.distance_fade_enabled = false

		_decals.append(decal)
		add_child(decal)
