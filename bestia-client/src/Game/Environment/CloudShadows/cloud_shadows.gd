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

## Metres across per decal. Three of these cover the camera in each axis, so the grid reaches 1.5 tiles in
## every direction - comfortably past [code]fog_depth_end[/code], where nothing is visible anyway.
@export_range(64, 1024, 32) var tile_metres: float = 256.0

## How deep each decal projects, in metres, centred on the camera.
##
## Has to span the terrain relief underneath it or shadows stop at the height the camera happens to be at.
## Generous rather than exact: the cost of a decal is its screen area, not its depth.
@export_range(64, 1024, 32) var depth_metres: float = 400.0

## How much darker the ground goes under the thickest part of a cloud, at full strength.
@export_range(0, 1, 0.01) var max_darkness: float = 0.45

## What a shadowed patch of ground is tinted toward.
##
## Not black, and not neutral. Ground in cloud shadow is still lit - by the sky, which is blue - so the
## shadow is a colour shift as much as a darkening. Black patches read as holes in the terrain.
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

## How far cover has to move before the mask is worth regenerating. [NoiseTexture2D] renders on a thread, so
## this is cheap - but not free, and cover drifts continuously.
const _COVERAGE_BAND := 0.08

var _weather: Node = null
var _clock: Node = null

var _decals: Array[Decal] = []
var _noise_texture: NoiseTexture2D = null

## Where the cloud field has drifted to, in metres. Grows without bound on purpose - it is only ever used
## modulo [member tile_metres], and a float carries far more session than anyone will play.
var _drift := Vector2.ZERO

## The sun-parallax offset, smoothed. See [method _sun_offset].
var _parallax := Vector2.ZERO

var _built_coverage := -1.0


func _ready() -> void:
	_weather = ConnectionManager.weather
	if _weather == null:
		push_warning("[clouds] no WeatherState; cloud shadows will not run.")

	_clock = ConnectionManager.world_clock

	_build_texture()
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

	var wind: Vector3 = _weather.Wind
	_drift += Vector2(wind.x, wind.z) * wind_multiplier * delta

	# Smoothed because the sun only moves every update_delay_seconds, and an unsmoothed parallax would step
	# the whole shadow field sideways once every ten seconds near sunrise, where the term grows fastest.
	_parallax = _parallax.lerp(_sun_offset(), 1.0 - exp(-delta / 2.0))

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
## The grid is aligned so that each decal starts exactly where the cloud field repeats, which is what lets it
## teleport by a whole tile without anything showing: the mask is seamless with period [member tile_metres],
## so the pattern either side of the jump is identical.
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


## Rebuilds the mask when the sky has changed enough to be worth it.
##
## Cover moves the ramp, which grows the patches; strength separately sets how dark they go. Both are needed,
## and the first is the one that is easy to skip: a fixed pattern fading up and down is the tell that gives a
## cheap effect away, because real cloud shadow gets larger before it gets darker.
func _ensure_coverage(coverage: float) -> void:
	if absf(coverage - _built_coverage) < _COVERAGE_BAND:
		return

	_built_coverage = coverage

	# The ramp is a property of the texture, so assigning it is what queues the regeneration.
	_noise_texture.color_ramp = _ramp_for(coverage)


## The mask ramp for a given cover: where the noise stops being sky and starts being cloud.
##
## Built fresh each time rather than by moving the existing points. [method Gradient.set_offset] re-sorts,
## so pushing the lower point past where the upper one currently sits swaps the two colours - and the
## threshold sweeps across the whole range as the sky changes, which is exactly when that happens. The
## symptom would be the shadows inverting into shafts of light.
func _ramp_for(coverage: float) -> Gradient:
	# Falls as cover rises, so more of the noise field clears the bar and the patches grow.
	var threshold := lerpf(0.85, 0.15, clampf(coverage, 0.0, 1.0))
	const EDGE := 0.22

	var ramp := Gradient.new()
	ramp.offsets = PackedFloat32Array([
		clampf(threshold - EDGE, 0.0, 1.0),
		clampf(threshold + EDGE, 0.0, 1.0)])

	# Black throughout; only the alpha carries the cloud. The tint is applied per decal through modulate, so
	# the mask stays a mask and the colour stays somewhere a designer can find it.
	ramp.colors = PackedColorArray([Color(0.0, 0.0, 0.0, 0.0), Color(0.0, 0.0, 0.0, 1.0)])

	return ramp


func _build_texture() -> void:
	var noise := FastNoiseLite.new()
	noise.noise_type = FastNoiseLite.TYPE_SIMPLEX_SMOOTH
	noise.fractal_type = FastNoiseLite.FRACTAL_FBM
	noise.fractal_octaves = 4
	noise.fractal_gain = 0.45
	noise.frequency = cloud_scale / float(texture_size)

	_noise_texture = NoiseTexture2D.new()
	_noise_texture.noise = noise
	_noise_texture.width = texture_size
	_noise_texture.height = texture_size
	_noise_texture.seamless = true
	_noise_texture.generate_mipmaps = true
	_noise_texture.color_ramp = _ramp_for(0.5)


func _build_decals() -> void:
	# Built here rather than authored into the scene because there are nine of them and they differ only by
	# where they are, which is decided per frame anyway.
	for i in _GRID * _GRID:
		var decal := Decal.new()
		decal.name = "CloudShadow%d" % i
		decal.size = Vector3(tile_metres, depth_metres, tile_metres)
		decal.texture_albedo = _noise_texture
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
