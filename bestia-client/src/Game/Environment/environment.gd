extends Node

## Drives the sun, the moon, the sky and the fog from the world clock and the weather.
##
## The lights and the WorldEnvironment are children of this same scene, so their shadow settings, angular
## sizes and fog distances stay where a designer would look for them - and so they are reached directly here
## rather than handed over by whatever scene this is dropped into. Only the clock and the weather come from
## outside, and both are allowed to be missing: this is a visual layer, and a client that has not been told
## the time or the sky should still be playable under the light the scene was authored with.
##
## [b]Why the sun's shadow cascades are split where they are.[/b] The camera orbits eight to thirty-six
## metres out, and Godot sizes each PSSM cascade from the bounding sphere of its frustum slice - so the
## split ratios, not the distance, decide the texel size of every shadow the player is looking at. Pushed
## out to 0.05 and 0.17 the whole orbit band falls inside one cascade, eleven to thirty-seven metres, at
## about 2.4 cm a texel. On the defaults the band straddles the first boundary instead, which is both
## coarser and puts the player on the split blend.
##
## [b]Lowering directional_shadow_max_distance does not help, it hurts.[/b] The obvious economy - shadows
## reach 220 m to meet fog_depth_end, the camera sees forty - is a trap, because the ratios are fractions
## of that distance. Cutting it to 140 drags the boundaries inward until the player stands in the third
## cascade, at 4.4 cm a texel rather than 2.4. Reach is nearly free here; the atlas size and the ratios are
## the only things that are not.

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

@export_range(0, 300, 1) var update_delay_seconds: int = 10

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

@export_group("Weather")

## What the daytime sky goes under a full overcast.
##
## Light greys, and that is the whole trick rather than an accident of taste. Ambient light is sampled from
## this sky, so a bright grey dome is what raises the fill light as the sun is dimmed - which is what cloud
## actually does, moving light out of one direction and into every direction. Darkening both together is what
## makes an overcast noon read as dusk.
@export var overcast_sky_top: Color = Color(0.560, 0.600, 0.655)
@export var overcast_sky_horizon: Color = Color(0.690, 0.700, 0.720)
@export var overcast_ground: Color = Color(0.400, 0.410, 0.425)
@export var overcast_fog: Color = Color(0.655, 0.680, 0.710)

## How much cloud deepens the night, in [0, 1] at full cover.
##
## The mirror of the palette above and the reason it needs one: cloud brightens a day and darkens a night,
## because at night there is nothing overhead for it to scatter back down.
@export_range(0, 1, 0.01) var overcast_night_darkening: float = 0.35

## How much of the sky's light reaches a shadowed surface, clear sky to closed deck.
##
## A pair rather than the single authored 1.0, because a shadow reads as crisp from two things - how narrow
## its edge is and how deep its interior goes - and only the first was ever driven. Ambient here is sampled
## from the sky dome, and the clear-day dome is a bright grey along the horizon, so a shadow at noon was
## filled nearly as well as one under cloud and the sun had to fight its own fill for contrast.
##
## Driven from WeatherLook.Diffusion, like the sun's disc and its energy, so the fill rises exactly as the
## direct light is taken away. Moving light out of one direction and into every direction is what cloud
## does; doing only half of it is what made an overcast noon and a clear one look alike.
##
## Turn sun_energy up with the clear end if lit surfaces now read dark. What is wanted here is the ratio
## between sun and fill, not the overall level.
@export_range(0.2, 1.5, 0.01) var clear_ambient_energy: float = 0.65
@export_range(0.2, 1.5, 0.01) var overcast_ambient_energy: float = 1.0

## Where the fog closes to in a whiteout, in metres. The authored distances are the clear-day end.
##
## Floored well above where a blizzard would put them if visibility alone decided, and the camera is why:
## fog depth is measured from the camera, which orbits out to thirty-six metres, so a fog that begins at six
## would swallow the player's own character before it swallowed anything else. Losing the world in a whiteout
## is the effect; losing the character you are steering is a bug.
@export_range(1, 100, 1) var whiteout_fog_begin: float = 22.0
@export_range(2, 200, 1) var whiteout_fog_end: float = 60.0

@export_subgroup("Lightning")

## Seconds between bolts. Redrawn after each one.
@export_range(1, 120, 0.5) var lightning_min_gap_seconds: float = 5.0
@export_range(1, 240, 0.5) var lightning_max_gap_seconds: float = 22.0

## How long a stroke takes to fade, in seconds. Short: the eye keeps a flash far longer than the sky does.
@export_range(0.05, 2, 0.01) var lightning_decay_seconds: float = 0.35

@export var lightning_energy: float = 3.2

@export var lightning_colour: Color = Color(0.86, 0.90, 1.0)

var _clock: Node = null
var _weather: Node = null

# This will trigger an immediate update after init.
var _last_updated_since: float = float(update_delay_seconds) + 1.0

# The last sky the throttled pass computed. Kept because the lightning runs unthrottled on top of it, and
# fog colour therefore has two writers a frame apart - so the slow one has to leave its answer somewhere the
# fast one can add to rather than overwrite.
var _base_fog_colour: Color = Color.WHITE

# The authored clear-day fog distances, read once. Weather only ever pulls them in from here.
var _clear_fog_begin: float = 0.0
var _clear_fog_end: float = 0.0

# What the last throttled pass was told, so a sky that drifts between passes can force an early one.
var _applied_overcast: float = -1.0
var _applied_visibility: float = -1.0
var _applied_haze: float = -1.0

var _flash: float = 0.0
var _next_strike: float = -1.0
var _strikes_left: int = 0

@onready var _sun: DirectionalLight3D = $Sun
@onready var _moon: DirectionalLight3D = $Moon
@onready var _flash_light: DirectionalLight3D = $Flash
@onready var _environment: Environment = ($WorldEnvironment as WorldEnvironment).environment

# The sky material is reached through the Environment rather than exported separately, because the two have
# to be the *same* sky - the one the background draws and the one ambient light is sampled from.
@onready var _sky: ProceduralSkyMaterial = _environment.sky.sky_material as ProceduralSkyMaterial

## How far overcast or visibility has to drift before the throttled pass is brought forward.
##
## The clock is what update_delay_seconds was chosen for: ten seconds of a sun that crosses the sky in eight
## real hours is nothing. Weather is not on that scale - a shower arriving would step the whole sky in one
## jump at the next pass, ten seconds after it started.
const _WEATHER_BAND := 0.02


func _ready() -> void:
	_clock = ConnectionManager.world_clock
	if _clock == null:
		push_warning("[daynight] no WorldClock; the scene's authored lighting will not change.")

	_weather = ConnectionManager.weather
	if _weather == null:
		push_warning("[daynight] no WeatherState; the sky will stay clear.")

	# Never casts shadows, and that is a decision rather than an oversight: a second shadow-casting
	# directional light doubles the shadow pass for a fill that is a sixth of the sun's brightness. The
	# sun is hidden outright at night, so nothing is paying for a shadow map nobody can see.
	_moon.shadow_enabled = false
	_moon.light_color = moon_colour

	# A third one, and the same reasoning applies twice over: it is on for a tenth of a second at a time.
	_flash_light.shadow_enabled = false
	_flash_light.light_color = lightning_colour
	_flash_light.visible = false

	_clear_fog_begin = _environment.fog_depth_begin
	_clear_fog_end = _environment.fog_depth_end


func _process(delta: float) -> void:
	if _clock == null or not _clock.IsAnchored():
		return

	var overcast := _overcast()
	var visibility := _visibility()

	_last_updated_since += delta

	if _last_updated_since >= float(update_delay_seconds) \
			or absf(overcast - _applied_overcast) > _WEATHER_BAND \
			or absf(visibility - _applied_visibility) > _WEATHER_BAND:
		_last_updated_since = 0.0
		_applied_overcast = overcast
		_applied_visibility = visibility

		var daylight: float = _clock.Daylight

		# Peaks at 1 exactly where daylight crosses a half - which is the middle of each ramp, and so the
		# moment the sun is on the horizon. One scalar gives both the blend and the sun's own warm cast.
		var twilight: float = (1.0 - absf(2.0 * daylight - 1.0)) * twilight_strength * _twilight_scale()

		_apply_lights(daylight, twilight)
		_apply_sky(daylight, twilight, overcast, visibility)

	# Unthrottled, and it has to be: a stroke is shorter than one throttled pass, so a flash applied on that
	# beat would be on screen for ten seconds or missed entirely.
	_tick_lightning(delta)


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

	# Both of these come off one curve - see WeatherLook.Diffusion. A broken deck leaves the sun where it was;
	# only a deck that has closed over dims it, and it widens it in the same breath.
	var dimming := _sun_energy_scale()

	# Widening the disc as well as dimming it. Cloud does not merely block light, it scatters it: an
	# overcast sky is one enormous area light, and its shadows have no edge left to speak of. Dimming alone
	# gives sharp black shadows in a grey world, which is what an eclipse looks like rather than a dull day.
	var softness := _sun_angular_degrees()

	_sun.rotation = _light_rotation(elevation, azimuth)
	_sun.light_energy = sun_energy * daylight * dimming
	_sun.light_color = sun_day_colour.lerp(sun_twilight_colour, twilight)
	_sun.light_angular_distance = softness

	# Hidden rather than merely dark. A DirectionalLight3D at zero energy still renders its shadow map,
	# and the whole of full night would be paying for a pass that contributes nothing.
	_sun.visible = daylight > 0.002

	# The anti-sun: it rises as the sun sets, which is free and is also roughly what a moon does.
	#
	# Cloud is between the player and both lights, so `dimming` takes the same cut out of each. A moon still
	# picking out every hillside through a blizzard is the tell that the weather stopped at the sun.
	_moon.rotation = _light_rotation(-elevation, azimuth + PI)
	_moon.light_energy = moon_energy * (1.0 - daylight) * dimming
	_moon.light_angular_distance = softness
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


func _apply_sky(daylight: float, twilight: float, overcast: float, visibility: float) -> void:
	_sky.sky_top_color = _weathered(
		sky_top_night, sky_top_day, sky_top_twilight, overcast_sky_top, overcast, daylight, twilight)
	_sky.sky_horizon_color = _weathered(
		sky_horizon_night, sky_horizon_day, sky_horizon_twilight, overcast_sky_horizon,
		overcast, daylight, twilight)
	_sky.ground_horizon_color = _weathered(
		ground_night, ground_day, ground_twilight, overcast_ground, overcast, daylight, twilight)

	# Fog is the loudest of the three. It is drawn over everything at distance, so a fog still lit for
	# midday is a pale band along the horizon of a night scene - which reads as the night not having
	# applied rather than as one setting having been missed.
	# Kinds whose air has a colour of its own tint the fog on top of the hour and the cloud. Applied last so
	# it survives them both: a sandstorm at dusk is sand-coloured, not orange, because what is between the
	# player and the horizon is sand rather than sky.
	_base_fog_colour = _weathered(
		fog_night, fog_day, fog_twilight, overcast_fog, overcast, daylight, twilight).lerp(
			_haze_colour(), _haze_tint())
	_environment.fog_light_color = _base_fog_colour

	# Rain, fog and snow are all things in the air between the player and the horizon, so they are the same
	# effect at different strengths and all of them belong here rather than in a per-kind branch.
	_environment.fog_depth_begin = lerpf(whiteout_fog_begin, _clear_fog_begin, visibility)
	_environment.fog_depth_end = lerpf(whiteout_fog_end, _clear_fog_end, visibility)

	# Here with the sky rather than with the lights, because this is the sky's own contribution: ambient is
	# sampled from the dome set above, and this only says how much of it arrives.
	_environment.ambient_light_energy = lerpf(clear_ambient_energy, overcast_ambient_energy, _diffusion())


## The palette entry for one surface: weather first, then the hour.
##
## The order is the point. Weather acts on the *ends* of the day/night blend - it greys the day and deepens
## the night - and only then does the hour choose between them. Blending toward a grey after the hour had
## chosen would put a midday grey over midnight, which is a sky lit from nowhere.
func _weathered(night: Color, day: Color, twilight: Color, overcast_colour: Color, overcast: float,
		daylight: float, twilight_weight: float) -> Color:
	var clouded_day := day.lerp(overcast_colour, overcast)
	var clouded_night := night.darkened(overcast_night_darkening * overcast)

	return clouded_night.lerp(clouded_day, daylight).lerp(twilight, twilight_weight)


func _tick_lightning(delta: float) -> void:
	if not _is_storming():
		if _flash > 0.0:
			_flash = 0.0
			_show_flash()

		_next_strike = -1.0
		_strikes_left = 0

		return

	if _next_strike < 0.0:
		_schedule_strike()

	_next_strike -= delta
	if _next_strike <= 0.0:
		_strike()

	if _flash > 0.0:
		_flash = maxf(_flash - delta / lightning_decay_seconds, 0.0)
		_show_flash()


func _strike() -> void:
	_flash = 1.0
	_strikes_left -= 1

	# A bolt somewhere else in the sky each time, so repeated strikes do not all light the world from the
	# same corner. Elevation stays high: this is the sky flashing, not a light at ground level.
	_flash_light.rotation = Vector3(deg_to_rad(randf_range(-80.0, -40.0)), randf_range(-PI, PI), 0.0)

	if _strikes_left > 0:
		# The second stroke of the same bolt, close enough behind to read as one flicker rather than two
		# separate bolts. Nearly every real flash does this and its absence is what makes a single clean
		# pulse look like a light switch.
		_next_strike = randf_range(0.05, 0.15)
	else:
		_schedule_strike()


func _schedule_strike() -> void:
	_next_strike = randf_range(lightning_min_gap_seconds, lightning_max_gap_seconds)
	_strikes_left = 2 if randf() < 0.55 else 1


func _show_flash() -> void:
	# Squared, so the fall is fast at the top and lingers at the bottom. A linear fade reads as a lamp being
	# turned down rather than as a discharge.
	var energy := _flash * _flash

	_flash_light.visible = energy > 0.002
	_flash_light.light_energy = lightning_energy * energy

	# The air lights up as well as the ground. Without this the flash stops at whatever the fog is drawn
	# over, and a storm at distance flickers only in the near field.
	_environment.fog_light_color = _base_fog_colour.lerp(lightning_colour, energy * 0.75)


## The weather readings, each with the value that means "no weather" when there is nobody to ask.
##
## Landed in a typed local rather than returned straight out of the ternary: a C# property crosses into
## GDScript as a Variant, and the analyser cannot see that the branch types agree.
func _diffusion() -> float:
	if _weather == null:
		return 0.0

	var value: float = _weather.Diffusion

	return value


func _overcast() -> float:
	if _weather == null:
		return 0.0

	var value: float = _weather.Overcast

	return value


func _visibility() -> float:
	if _weather == null:
		return 1.0

	var value: float = _weather.Visibility

	return value


func _sun_energy_scale() -> float:
	if _weather == null:
		return 1.0

	var value: float = _weather.SunEnergyScale

	return value


func _sun_angular_degrees() -> float:
	if _weather == null:
		return 0.0

	var value: float = _weather.SunAngularDegrees

	return value


func _twilight_scale() -> float:
	if _weather == null:
		return 1.0

	var value: float = _weather.TwilightScale

	return value


func _haze_colour() -> Color:
	if _weather == null:
		return Color.WHITE

	var value: Color = _weather.HazeColour

	return value


func _haze_tint() -> float:
	if _weather == null:
		return 0.0

	var value: float = _weather.HazeTint

	return value


func _is_storming() -> bool:
	if _weather == null:
		return false

	var storming: bool = _weather.HasLightning

	return storming
