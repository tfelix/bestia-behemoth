extends Node3D

## Rain, snow and blown dust, falling around the camera and leaning into the wind.
##
## Three [GPUParticles3D] emitters, of which at most one is ever running - a sky does one thing at a time, and
## [code]WeatherLookTest[/code] holds it to that. They are built here rather than authored into the scene
## because their meshes and textures are generated too, so there would be nothing in the scene to look at.
##
## [b]The emitters follow the camera and are never parented to it.[/b] The camera is a child of the owned
## entity and is freed with it on logout, and [code]camera_follow.gd[/code] lerps it behind its own pivot, so
## anything hanging off it would inherit both the lifetime and the lag. They track
## [method Viewport.get_camera_3d] instead, with [member GPUParticles3D.local_coords] off so that moving the
## emitter does not drag the drops already in the air along with it.
##
## Wind enters in exactly one place: it tilts [member ParticleProcessMaterial.direction] away from straight
## down. Everything else follows - the streaks lean because
## [constant GPUParticles3D.TRANSFORM_ALIGN_Z_BILLBOARD_Y_TO_VELOCITY] points them along their own velocity,
## and the field drifts because that is where the drops are going.

## How far above the camera each kind appears, in metres, and how long it then has to live.
##
## [b]Paired, and they have to be read together.[/b] Lifetime times fall speed is how far a particle actually
## gets, so between them these decide whether anything reaches the ground: snow at 1.4 m/s given a rain-sized
## lifetime falls four metres and then hangs in the air above the player forever.
##
## Kept short as well as sufficient. A particle lives in world space, so a long-lived one is still where it
## was when the player walked away from it - and the field thins out ahead of anyone moving.
##
## The lifetimes are a ceiling rather than a setting: [method _drive] shortens them further whenever the wind
## would otherwise carry a particle clean out of the box before it died.
@export_range(2, 60, 1) var rain_spawn_height: float = 20.0
@export_range(-20, 60, 1) var snow_spawn_height: float = 4.0
@export_range(-20, 60, 1) var dust_spawn_height: float = 2.0

@export_range(0.3, 30, 0.1) var rain_lifetime: float = 2.4
@export_range(0.3, 30, 0.1) var snow_lifetime: float = 8.0
@export_range(0.3, 30, 0.1) var dust_lifetime: float = 3.0

## Half-width of the spawn box, in metres.
##
## Wide enough to cover the ground in frame at full zoom-out, and no wider. Every extra metre spreads the
## same particle budget over more volume, and the ones that land outside the view are paid for and never
## seen - which is what a field that looks like drizzle in a downpour turns out to be.
@export_range(10, 120, 1) var spawn_radius: float = 32.0

## Metres per second straight down, before the wind tips it over.
@export_range(1, 40, 0.5) var rain_fall_speed: float = 17.0

@export_range(0.2, 10, 0.1) var snow_fall_speed: float = 2.0

@export_range(0, 5, 0.1) var dust_fall_speed: float = 0.4

## How much of the reported wind each kind actually carries sideways.
##
## Rain is heavy and reaches terminal velocity quickly, so it very nearly goes where the air goes. Snow has
## far more drag per gram and gets carried further than the wind that is pushing it; dust further still,
## which is most of why a sandstorm reads as horizontal.
@export_range(0, 3, 0.05) var rain_wind_factor: float = 0.9

@export_range(0, 4, 0.05) var snow_wind_factor: float = 1.4

@export_range(0, 6, 0.05) var dust_wind_factor: float = 2.2

## Particles in the air at the heaviest of each. Scaled down from here by
## [member GPUParticles3D.amount_ratio], which - unlike [member GPUParticles3D.amount] - does not reallocate
## the buffer and restart the system.
@export_range(100, 8000, 100) var rain_drops: int = 2400

@export_range(100, 8000, 100) var snow_flakes: int = 2200

@export_range(100, 8000, 100) var dust_motes: int = 3600

## Sizes are set where the emitters are built, and are all generously larger than life - a real snowflake at
## thirty metres is well under a pixel, so a physically sized field renders as an empty sky that the profiler
## nevertheless charges for.
@export var rain_colour: Color = Color(0.70, 0.77, 0.88, 0.40)

@export var snow_colour: Color = Color(0.96, 0.97, 1.0, 0.85)

@export var dust_colour: Color = Color(0.76, 0.63, 0.42, 0.30)

## Below this rate an emitter is switched off rather than run at a trickle.
const _CUTOFF := 0.02

## How far the wind has to move before the emitters are retuned. Purely to keep a per-frame uniform write off
## the profile; the visual difference below this is nothing.
const _WIND_BAND := 0.15

## The longest simulation a restart is allowed to run up front, in seconds.
##
## Preprocessing is what makes rain starting look like rain that was already falling rather than a curtain
## descending from twenty metres up, but it is simulated in one go on the frame it happens - so the whole of
## a long lifetime would arrive as a hitch. Past this the field fills in over its first few seconds instead,
## which the rate ramping up hides anyway.
const _MAX_PREPROCESS := 4.0

var _weather: Node = null

var _rain: GPUParticles3D = null
var _snow: GPUParticles3D = null
var _dust: GPUParticles3D = null

var _last_wind := Vector3(9999.0, 0.0, 0.0)


func _ready() -> void:
	_weather = ConnectionManager.weather
	if _weather == null:
		push_warning("[precipitation] no WeatherState; nothing will fall.")

	var soft_dot := _build_dot_texture()

	# The box heights are what separate the two shapes of weather, and the camera pitch is why.
	#
	# Rain falls seventeen metres a second, so a thin sheet twenty metres up sweeps the whole downward-looking
	# view cone within its life. Snow and dust do not: at two metres a second a flake born overhead is still
	# level with the camera when it dies, and the camera looks *down* - so the entire field lives above the
	# frame. Both get a tall box centred near the camera instead, and are in the view from the moment they
	# exist rather than having to fall into it.
	_rain = _build_emitter("Rain", rain_drops, rain_lifetime, rain_colour, null,
		Vector2(0.035, 0.9), 2.0, GPUParticles3D.TRANSFORM_ALIGN_Z_BILLBOARD_Y_TO_VELOCITY)

	_snow = _build_emitter("Snow", snow_flakes, snow_lifetime, snow_colour, soft_dot,
		Vector2(0.26, 0.26), 30.0, GPUParticles3D.TRANSFORM_ALIGN_Z_BILLBOARD)

	_dust = _build_emitter("Dust", dust_motes, dust_lifetime, dust_colour, soft_dot,
		Vector2(0.38, 0.38), 16.0, GPUParticles3D.TRANSFORM_ALIGN_Z_BILLBOARD)

	# Snow tumbles as it comes down; rain does not, and dust is too small to read as anything but a haze.
	var snow_process := _snow.process_material as ParticleProcessMaterial
	snow_process.angle_min = -180.0
	snow_process.angle_max = 180.0
	snow_process.angular_velocity_min = -40.0
	snow_process.angular_velocity_max = 40.0
	_turbulence(snow_process, 1.6, 0.18)

	_turbulence(_dust.process_material as ParticleProcessMaterial, 4.0, 0.30)


func _process(_delta: float) -> void:
	if _weather == null:
		return

	var camera := get_viewport().get_camera_3d()
	if camera == null:
		_rain.emitting = false
		_snow.emitting = false
		_dust.emitting = false
		return

	var wind: Vector3 = _weather.Wind
	var retune := wind.distance_to(_last_wind) > _WIND_BAND
	if retune:
		_last_wind = wind

	# Landed in typed locals first: a C# property crosses into GDScript as a Variant, and passing one
	# straight into a typed parameter hides its type from the analyser at the call site.
	var rain_rate: float = _weather.RainRate
	var snow_rate: float = _weather.SnowRate
	var dust_rate: float = _weather.DustRate

	_drive(_rain, rain_rate, wind, rain_fall_speed, rain_wind_factor, rain_spawn_height, rain_lifetime,
		camera, retune)
	_drive(_snow, snow_rate, wind, snow_fall_speed, snow_wind_factor, snow_spawn_height, snow_lifetime,
		camera, retune)
	_drive(_dust, dust_rate, wind, dust_fall_speed, dust_wind_factor, dust_spawn_height, dust_lifetime,
		camera, retune)


## Points one emitter at the wind, positions it over the camera, and sets how much of it is running.
func _drive(emitter: GPUParticles3D, rate: float, wind: Vector3, fall_speed: float, wind_factor: float,
		spawn_height: float, base_lifetime: float, camera: Camera3D, retune: bool) -> void:
	if rate < _CUTOFF:
		emitter.emitting = false
		return

	var travel := Vector3(wind.x * wind_factor, -fall_speed, wind.z * wind_factor)
	var speed := travel.length()

	# Cut the life short once a particle would outlive the box it was born in. In a gale a mote of dust does
	# fifty metres a second, so an authored three seconds carries it a hundred and fifty - and the field
	# becomes a long thin streak that the camera is somewhere in the middle of, if it is in it at all.
	var span := spawn_radius * 2.0 + absf(spawn_height)
	var lifetime := clampf(span / speed, 0.4, base_lifetime)

	emitter.lifetime = lifetime

	# Upwind of the camera by half of what a particle covers in its life, so the field ends up centred on the
	# player rather than blowing off downwind of them. Straight overhead when there is no wind, which falls
	# out of the same sum rather than needing a case of its own.
	#
	# Capped even so. The two together are what keep the box over the player: the shortened lifetime stops it
	# wanting to be far away, and this stops it getting there when the sum still says it should.
	var lead := Vector3(travel.x, 0.0, travel.z) * lifetime * 0.5
	lead = lead.limit_length(spawn_radius * 0.8)

	emitter.global_position = camera.global_position + Vector3(0.0, spawn_height, 0.0) - lead

	if retune or not emitter.emitting:
		var process := emitter.process_material as ParticleProcessMaterial
		process.direction = travel / speed
		process.initial_velocity_min = speed
		process.initial_velocity_max = speed

	# amount_ratio and not amount: the latter reallocates the buffer and restarts the system, which would
	# empty the sky and refill it from the top every time a WeatherSMSG arrived.
	emitter.amount_ratio = clampf(rate, 0.0, 1.0)

	# Assigning emitting = true restarts the system, so it is only ever assigned when it was false. Without
	# the guard the field would reset every frame and nothing would appear to fall at all.
	if not emitter.emitting:
		emitter.emitting = true


func _build_emitter(node_name: String, amount: int, lifetime: float, colour: Color, texture: Texture2D,
		quad: Vector2, box_height: float, align: int) -> GPUParticles3D:
	var mesh := QuadMesh.new()
	mesh.size = quad

	var material := StandardMaterial3D.new()
	material.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	material.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	material.albedo_color = colour
	material.albedo_texture = texture
	material.cull_mode = BaseMaterial3D.CULL_DISABLED
	material.vertex_color_use_as_albedo = true
	material.disable_receive_shadows = true

	var process := ParticleProcessMaterial.new()
	process.emission_shape = ParticleProcessMaterial.EMISSION_SHAPE_BOX
	process.emission_box_extents = Vector3(spawn_radius, box_height * 0.5, spawn_radius)

	# Straight-line travel at terminal velocity, so gravity has nothing left to do. Acceleration would make
	# each streak steepen over its own length, which is not what falling rain looks like - and it is also
	# what would stop the velocity alignment below producing one consistent angle across the field.
	process.gravity = Vector3.ZERO
	process.spread = 3.0
	process.scale_min = 0.7
	process.scale_max = 1.3
	process.color_ramp = _build_fade_ramp()

	var emitter := GPUParticles3D.new()
	emitter.name = node_name
	emitter.amount = amount
	emitter.lifetime = lifetime
	emitter.transform_align = align
	emitter.draw_pass_1 = mesh
	emitter.material_override = material
	emitter.process_material = process
	emitter.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	emitter.emitting = false

	# World space: the emitter chases the camera every frame, and without this every drop already falling
	# would be dragged along with it.
	emitter.local_coords = false

	emitter.preprocess = minf(lifetime * 0.9, _MAX_PREPROCESS)

	# At the frame rate rather than the default thirty: rain crosses the screen fast enough for a fixed step
	# to read as strobing.
	emitter.fixed_fps = 0

	# Culling is against this box, in the emitter's own space - and with local_coords off the particles have
	# left that space entirely. The default would fit a box to an emitter that never moves relative to
	# itself, and cull the whole field the moment the camera looked away from its centre.
	emitter.visibility_aabb = AABB(
		Vector3(-spawn_radius * 2.0, -lifetime * 40.0, -spawn_radius * 2.0),
		Vector3(spawn_radius * 4.0, lifetime * 80.0, spawn_radius * 4.0))

	add_child(emitter)

	return emitter


func _turbulence(process: ParticleProcessMaterial, strength: float, noise_scale: float) -> void:
	process.turbulence_enabled = true
	process.turbulence_noise_strength = strength
	process.turbulence_noise_scale = noise_scale
	process.turbulence_noise_speed = Vector3(0.3, 0.2, 0.3)


## Fades a particle out over the last of its life.
##
## Doing the work of ground collision without any: nothing stops a drop at the terrain, so without this they
## would sink through it at full opacity and be visible from underneath. Fading means the ones that get that
## far are already gone.
func _build_fade_ramp() -> GradientTexture1D:
	var gradient := Gradient.new()
	gradient.offsets = PackedFloat32Array([0.0, 0.08, 0.72, 1.0])
	gradient.colors = PackedColorArray([
		Color(1.0, 1.0, 1.0, 0.0),
		Color(1.0, 1.0, 1.0, 1.0),
		Color(1.0, 1.0, 1.0, 1.0),
		Color(1.0, 1.0, 1.0, 0.0)])

	var ramp := GradientTexture1D.new()
	ramp.gradient = gradient

	return ramp


## A soft round dot, so a flake is a flake and not a square.
func _build_dot_texture() -> GradientTexture2D:
	var gradient := Gradient.new()
	gradient.offsets = PackedFloat32Array([0.0, 0.55, 1.0])
	gradient.colors = PackedColorArray([
		Color(1.0, 1.0, 1.0, 1.0),
		Color(1.0, 1.0, 1.0, 0.9),
		Color(1.0, 1.0, 1.0, 0.0)])

	var texture := GradientTexture2D.new()
	texture.gradient = gradient
	texture.fill = GradientTexture2D.FILL_RADIAL
	texture.fill_from = Vector2(0.5, 0.5)
	texture.fill_to = Vector2(1.0, 0.5)
	texture.width = 64
	texture.height = 64

	return texture
