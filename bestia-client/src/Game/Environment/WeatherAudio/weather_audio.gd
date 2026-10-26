extends Node

## The sound of the weather: rain and wind loops that follow the sky, and thunder after each lightning strike.
##
## Every stream is an export and may be left empty, which keeps that sound silent. All of it plays on the
## Ambience bus.

@export var rain_loop: AudioStream
@export var wind_loop: AudioStream

## Sharp cracks for strikes close by, and long rumbles for the rest.
@export var thunder_near: Array[AudioStream] = []
@export var thunder_far: Array[AudioStream] = []

## Where a strike stops sounding like a crack and starts sounding like a rumble, in metres.
@export var near_thunder_metres: float = 800.0

## Wind speed in m/s at which the wind loop is at full volume.
@export var full_wind_speed: float = 22.0

const _SPEED_OF_SOUND := 343.0

var _weather: Node = null
var _rain := AudioStreamPlayer.new()
var _wind := AudioStreamPlayer.new()


func _ready() -> void:
	_weather = ConnectionManager.weather

	_setup_loop(_rain, rain_loop)
	_setup_loop(_wind, wind_loop)

	var environment := get_parent()
	if environment.has_signal(&"lightning_struck"):
		environment.lightning_struck.connect(_on_lightning_struck)


func _process(_delta: float) -> void:
	if _weather == null:
		return

	_rain.volume_linear = _weather.RainRate
	var gale := clampf(_weather.WindSpeed / full_wind_speed, 0.0, 1.0)
	_wind.volume_linear = gale
	_wind.pitch_scale = lerpf(0.85, 1.2, gale)


func _setup_loop(player: AudioStreamPlayer, stream: AudioStream) -> void:
	player.bus = Audio.AMBIENCE
	player.volume_linear = 0.0
	add_child(player)

	if stream == null:
		return

	player.stream = stream
	# Restarted by hand rather than trusting the import's loop flag, which a dropped-in file may not have set.
	player.finished.connect(player.play)
	player.play()


func _on_lightning_struck(ground: Vector3) -> void:
	var camera := get_viewport().get_camera_3d()
	var distance := camera.global_position.distance_to(ground) if camera != null else near_thunder_metres

	var pool := thunder_near if distance < near_thunder_metres else thunder_far
	if pool.is_empty():
		return

	var thunder := AudioStreamPlayer3D.new()
	thunder.stream = pool.pick_random()
	thunder.bus = Audio.AMBIENCE
	thunder.attenuation_model = AudioStreamPlayer3D.ATTENUATION_LOGARITHMIC
	# Thunder carries for kilometres; the default unit size would make a strike at 2 km inaudible.
	thunder.unit_size = 400.0
	thunder.finished.connect(thunder.queue_free)
	add_child(thunder)
	thunder.global_position = ground

	get_tree().create_timer(distance / _SPEED_OF_SOUND).timeout.connect(thunder.play)
