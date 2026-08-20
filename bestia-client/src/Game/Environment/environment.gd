extends Node

## Drives the sun, the moon, the sky and the fog from the world clock.

## How high the sun climbs at its peak, in degrees.
##
## Not 90. A sun directly overhead flattens every slope in the world at midday, which costs exactly the
## relief the terrain's normal maps were added to show.
@export_range(10, 89, 1) var peak_elevation_degrees: float = 68.0

## How close to the horizon either light is allowed to get, in degrees. The arc flattens out here; the
## colour and the energy carry the rest of the sunset.
##
## A shadow is height / tan(elevation) long, so its far end runs away as 1/elevation² while the sun
## itself is still turning at a placid hundredth of a degree per second. Twelve real minutes before
## sunset a ten-metre tree already throws a hundred-metre shadow whose tip is crossing the ground at a
## quarter of a metre per second, and six minutes later that tip is doing walking pace - which reads on
## screen as the shadow's far edge sliding and smearing rather than as the sun going down. The same
## geometry stretches every shadow-map texel by 1/sin(elevation), so the edge going soft and the edge
## going loose arrive together.
@export_range(1, 30, 1) var min_elevation_degrees: float = 9.0

## How far the sun's bearing swings between rising and setting, in degrees, centred on
## base_azimuth_degrees.
##
## Well short of the 180 a real sun sweeps. Shadows that rotate through a half circle over a session read
## as the world spinning rather than as the day passing, because the camera is fixed relative to the
## ground and there is nothing on screen to attribute the motion to.
@export_range(0, 180, 1) var sweep_degrees: float = 120.0

## The bearing the sun crosses at its peak. Shadows point away from this at midday.
@export_range(-180, 180, 1) var base_azimuth_degrees: float = 40.0

@export var sun_energy: float = 1.0

## How bright the moon is. Small, but never zero.
##
## A night lit only by ambient sky is flat - no shape on the terrain and no direction to anything. This
## is what keeps a hillside at midnight readable as a hillside.
@export var moon_energy: float = 0.16

@export var sun_day_colour: Color = Color(1.0, 0.96, 0.88)

## What the sun goes as it nears the horizon, and what dawn and dusk take their cast from.
@export var sun_twilight_colour: Color = Color(1.0, 0.52, 0.26)

@export var moon_colour: Color = Color(0.55, 0.67, 1.0)

@export var sky_top_day: Color = Color(0.845, 0.906, 0.989)
@export var sky_top_twilight: Color = Color(0.24, 0.24, 0.46)
@export var sky_top_night: Color = Color(0.021, 0.030, 0.086)

@export var sky_horizon_day: Color = Color(0.646, 0.656, 0.671)
@export var sky_horizon_twilight: Color = Color(0.86, 0.42, 0.24)
@export var sky_horizon_night: Color = Color(0.055, 0.075, 0.15)

@export var ground_day: Color = Color(0.646, 0.656, 0.671)
@export var ground_twilight: Color = Color(0.33, 0.22, 0.20)
@export var ground_night: Color = Color(0.026, 0.030, 0.048)

@export var fog_day: Color = Color(0.630, 0.729, 0.829)
@export var fog_twilight: Color = Color(0.72, 0.42, 0.30)
@export var fog_night: Color = Color(0.045, 0.060, 0.115)

## How much of the twilight palette the crossover actually gets, in [0, 1].
##
## A full-strength orange at every sunrise and sunset is a postcard the first time and a nuisance by the
## third, and the player will see three in a session. Turn it up to check the palette; leave it here to
## play under it.
@export_range(0, 1, 0.01) var twilight_strength: float = 0.75

var _clock: Node = null
@onready var _sun: DirectionalLight3D = $Sun
@onready var _moon: DirectionalLight3D = $Moon
@onready var _world_environment: WorldEnvironment = $WorldEnvironment
var _environment: Environment = null
var _sky: ProceduralSkyMaterial = null


func _ready() -> void:
	_clock = ConnectionManager.world_clock
	if _clock == null:
		push_warning("[daynight] no WorldClock; the scene's authored lighting will not change.")

	_environment = _world_environment.environment if _world_environment != null else null

	# The sky material is reached through the Environment rather than exported separately, because the
	# two have to be the *same* sky - the one the background draws and the one ambient light is sampled
	# from.
	if _environment != null and _environment.sky != null:
		_sky = _environment.sky.sky_material as ProceduralSkyMaterial

	if _sky == null:
		push_warning(
			"[daynight] the environment has no ProceduralSkyMaterial; the sky will not follow the " +
			"clock. Lights and fog still will.")

	if _moon != null:
		# Never casts shadows, and that is a decision rather than an oversight: a second shadow-casting
		# directional light doubles the shadow pass for a fill that is a sixth of the sun's brightness.
		# The sun is hidden outright at night, so nothing is paying for a shadow map nobody can see.
		_moon.shadow_enabled = false
		_moon.light_color = moon_colour


func _process(_delta: float) -> void:
	if _clock == null or not _clock.IsAnchored():
		return

	var daylight: float = _clock.Daylight

	# Peaks at 1 exactly where daylight crosses a half - which is the middle of each ramp, and so the
	# moment the sun is on the horizon. One scalar gives both the blend and the sun's own warm cast.
	var twilight: float = (1.0 - absf(2.0 * daylight - 1.0)) * twilight_strength

	_apply_lights(daylight, twilight)
	_apply_sky(daylight, twilight)


func _apply_lights(daylight: float, twilight: float) -> void:
	var progress: float = _solar_progress(_clock.HourOfDay)

	# The arc the sun would follow if the horizon were not in the way, which both lights are placed off:
	# the moon is this turned around, so deriving it from the raw angle rather than from the sun's own
	# floored rotation is what lets the moon rise while the sun is pinned above the skyline.
	#
	# The arc is pinned to the twilight ramps rather than to the full-day band: the sun crosses zero at
	# the *midpoint* of each ramp, so sunrise happens when the light is half up. That is what makes the
	# orange land at the same moment the sun is on the skyline instead of an hour off it.
	#
	# Elevation straight from the light level would hold the sun at its peak for the whole fourteen-hour
	# day, and a sun that does not move casts shadows that do not move. This is its own curve.
	var elevation: float = deg_to_rad(peak_elevation_degrees) * sin(PI * progress)
	var azimuth: float = deg_to_rad(base_azimuth_degrees + (progress - 0.5) * sweep_degrees)

	if _sun != null:
		_sun.rotation = _light_rotation(elevation, azimuth)
		_sun.light_energy = sun_energy * daylight
		_sun.light_color = sun_day_colour.lerp(sun_twilight_colour, twilight)

		# Hidden rather than merely dark. A DirectionalLight3D at zero energy still renders its shadow
		# map, and the whole of full night would be paying for a pass that contributes nothing.
		_sun.visible = daylight > 0.002

	if _moon != null:
		# The anti-sun: it rises as the sun sets, which is free and is also roughly what a moon does.
		_moon.rotation = _light_rotation(-elevation, azimuth + PI)
		_moon.light_energy = moon_energy * (1.0 - daylight)
		_moon.visible = daylight < 0.998


## A bearing and an elevation as an Euler rotation for a light pointing along its own -Z, with the
## elevation held at or above min_elevation_degrees.
##
## The floor is applied here rather than to the arc so that the arc stays the honest astronomy and this
## stays the one place either light is placed. A light whose turn is past the floor simply stops
## descending; nothing else about the hour it represents changes.
func _light_rotation(elevation: float, azimuth: float) -> Vector3:
	return Vector3(-maxf(elevation, deg_to_rad(min_elevation_degrees)), azimuth, 0.0)


## How far through its arc the sun is: 0 at sunrise, 0.5 at solar noon, 1 at sunset, and on to 2 at the
## next sunrise.
##
## Two arcs and not one, which is the whole subtlety here. The day is fourteen hours between the horizon
## crossings and the night is ten, so running the night on the day's scale leaves the sun at its nadir at
## the moment it should be rising - and then snapping to the horizon as the branch changes. Each half
## therefore gets its own denominator, which makes the sine continuous across both crossings.
##
## Past 1 the sine is negative, putting the sun below the horizon, which is where the moon's mirrored
## rotation wants it.
func _solar_progress(hour_of_day: float) -> float:
	var sunrise: float = _clock.SunriseHour
	var sunset: float = _clock.SunsetHour
	var hours_per_day: float = _clock.HoursPerDay

	if hour_of_day >= sunrise and hour_of_day < sunset:
		return (hour_of_day - sunrise) / (sunset - sunrise)

	# Before sunrise is the tail of the previous night, so it counts on from that night's sunset rather
	# than restarting - otherwise the moon would cross the sky twice between dusk and dawn.
	var hour: float = hour_of_day + hours_per_day if hour_of_day < sunrise else hour_of_day
	var night_hours: float = hours_per_day - (sunset - sunrise)

	return 1.0 + (hour - sunset) / night_hours


func _apply_sky(daylight: float, twilight: float) -> void:
	if _sky != null:
		_sky.sky_top_color = _blend(sky_top_night, sky_top_day, sky_top_twilight, daylight, twilight)
		_sky.sky_horizon_color = _blend(
			sky_horizon_night, sky_horizon_day, sky_horizon_twilight, daylight, twilight)
		_sky.ground_horizon_color = _blend(ground_night, ground_day, ground_twilight, daylight, twilight)

	# Fog is the loudest of the three. It is drawn over everything at distance, so a fog still lit for
	# midday is a pale band along the horizon of a night scene - which reads as the night not having
	# applied rather than as one setting having been missed.
	if _environment != null:
		_environment.fog_light_color = _blend(fog_night, fog_day, fog_twilight, daylight, twilight)


## Night to day by the light level, then toward the twilight cast by how close the crossover is.
static func _blend(night: Color, day: Color, twilight: Color, daylight: float, twilight_weight: float) -> Color:
	return night.lerp(day, daylight).lerp(twilight, twilight_weight)
