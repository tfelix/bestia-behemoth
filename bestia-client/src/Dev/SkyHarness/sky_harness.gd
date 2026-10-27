extends Node3D

## Renders the sky at fixed hours and weathers, from a sky camera and from the game camera's angle.
##
## Run it with a window - the headless renderer draws nothing:
##   DISPLAY=:0 godot --path bestia-client/src res://Dev/SkyHarness/SkyHarness.tscn -- --out=DIR [--shots=a,b]
##
## The clock is anchored by hand because environment.gd does nothing until it is, and it is all but stopped so a
## shot shows the hour it asked for rather than one a few seconds later.

## WeatherSMSG.Kind ordinals. The C# enum is nested in the message class, where GDScript cannot name it.
const CLEAR := 0
const CLOUDY := 1
const THUNDERSTORM := 5

## name, hour, day of the month, weather kind, cloud cover, wind m/s, mood strength, which way to look.
const SHOTS := [
	["noon_clear", 13.0, 15, CLEAR, 0.0, 3.0, 0.0, "anti_sun"],
	["noon_cumulus", 11.0, 15, CLEAR, 0.45, 6.0, 0.0, "anti_sun"],
	["sunset", 20.95, 15, CLEAR, 0.35, 4.0, 0.0, "sun"],
	["night_full_moon", 1.0, 15, CLEAR, 0.25, 3.0, 0.0, "moon"],
	["night_crescent", 4.6, 27, CLEAR, 0.2, 3.0, 0.0, "moon"],
	# What a live server sent on 2026-10-27: a dry grey day with a gale under it.
	["cloudy_gale", 12.0, 15, CLOUDY, 0.71, 20.0, 0.0, "anti_sun"],
	["storm", 15.0, 15, THUNDERSTORM, 1.0, 18.0, 0.0, "anti_sun"],
	["doomsday", 20.8, 15, CLEAR, 0.4, 5.0, 1.0, "sun"],
]

## Frames before the first capture. SDFGI paints black blotches until it has settled.
const WARMUP_FRAMES := 700

## Frames between shots: weather eases in over five seconds and SDFGI has to catch up with the new light.
const SETTLE_FRAMES := 360

const SECONDS_PER_DAY := 24 * 3600

## The game's spring arm at its shallowest pitch and its longest reach, which shows the most sky a player sees.
const GAME_PITCH_DEGREES := -20.0
const GAME_DISTANCE := 36.0

var _out_dir := "user://sky_harness"
var _only: PackedStringArray = []
var _warmup := WARMUP_FRAMES
var _settle := SETTLE_FRAMES

@onready var _environment: Node = $TerrainTestbed/Environment
@onready var _sky_camera: Camera3D = $SkyCamera
@onready var _game_camera: Camera3D = $GameCamera


func _ready() -> void:
	for arg in OS.get_cmdline_user_args():
		if arg.begins_with("--out="):
			_out_dir = arg.trim_prefix("--out=")
		elif arg.begins_with("--shots="):
			_only = arg.trim_prefix("--shots=").split(",")
		elif arg.begins_with("--frames="):
			_warmup = int(arg.trim_prefix("--frames="))
			_settle = _warmup

	DirAccess.make_dir_recursive_absolute(_out_dir)
	_run.call_deferred()


func _run() -> void:
	var first := true

	for shot in SHOTS:
		if not _only.is_empty() and not _only.has(shot[0]):
			continue

		print("[sky-harness] shot ", shot[0])
		_apply(shot)
		await _frames(_warmup if first else _settle)
		first = false

		await _capture(_sky_camera, "%s_sky" % shot[0])
		await _capture(_game_camera, "%s_game" % shot[0])

	get_tree().quit()


func _apply(shot: Array) -> void:
	var info := WorldInfoSMSG.new()
	info.WorldAgeBestiaSeconds = float((shot[2] - 1) * SECONDS_PER_DAY) + shot[1] * 3600.0
	info.TimeSpeedFactor = 0.0001
	info.HoursPerDay = 24
	info.DaysPerMonth = 30
	info.MonthsPerYear = 4
	info.NightEndHour = 4
	info.DawnEndHour = 6
	info.DuskStartHour = 20
	info.NightStartHour = 22
	ConnectionManager.world_clock.Anchor(info)

	var weather: Node = ConnectionManager.weather
	weather.DebugOverrideEnabled = true
	weather.DebugKind = shot[3]
	weather.DebugIntensity = 0.8
	weather.DebugCloudCover = shot[4]
	weather.DebugWindSpeed = shot[5]
	weather.DebugWindDirectionDegrees = 30.0

	# A shot shows the weather it asked for, not one still easing in from the previous shot.
	weather.SkySeconds = 0.2
	weather.WindSeconds = 0.2
	weather.WindSpeedSeconds = 0.2

	if shot[6] > 0.0 and _environment.mood == null:
		_environment.mood = load("res://Game/Environment/Sky/moods/doomsday.tres")
	_environment.mood_strength = shot[6]

	# The environment only re-reads the clock every update_delay_seconds; a jump in time has to cut that short.
	_environment.set("_last_updated_since", 1.0e9)

	_aim.call_deferred(shot[7])


## Points both cameras at the sun, away from it, or at the moon, once the lights have moved for this hour.
func _aim(facing: String) -> void:
	await _frames(2)

	var toward := Vector3.FORWARD
	match facing:
		"sun":
			toward = ($TerrainTestbed/Environment/Sun as Node3D).global_basis.z
		"anti_sun":
			toward = -($TerrainTestbed/Environment/Sun as Node3D).global_basis.z
		"moon":
			toward = ($TerrainTestbed/Environment/Moon as Node3D).global_basis.z

	var flat := Vector3(toward.x, 0.0, toward.z).normalized()
	var yaw := atan2(-flat.x, -flat.z)

	_sky_camera.position = Vector3(0, 30, 0)
	# Tilted up to what it faces, so a moon near the zenith is still in frame.
	var elevation := clampf(rad_to_deg(asin(toward.y)), 10.0, 60.0) if facing == "moon" else 28.0
	_sky_camera.rotation = Vector3(deg_to_rad(elevation), yaw, 0.0)

	# Behind and above the field's centre, as the spring arm would hold it behind a player standing there.
	var pitch := deg_to_rad(GAME_PITCH_DEGREES)
	var target := Vector3(0, 20, 0)
	_game_camera.position = target - Vector3(sin(yaw), 0, cos(yaw)) * cos(pitch) * -GAME_DISTANCE \
			+ Vector3.UP * -sin(pitch) * GAME_DISTANCE
	_game_camera.rotation = Vector3(pitch, yaw, 0.0)


func _capture(camera: Camera3D, name: String) -> void:
	camera.current = true

	# The render-debug readout is an overlay on the viewport, so it would land in every image.
	var readout: CanvasLayer = RenderDebug.get("_layer")
	if readout != null:
		readout.visible = false

	await _frames(3)

	var path := _out_dir.path_join(name + ".png")
	get_viewport().get_texture().get_image().save_png(path)
	print("[sky-harness] wrote ", path)


func _frames(count: int) -> void:
	for i in count:
		await get_tree().process_frame
