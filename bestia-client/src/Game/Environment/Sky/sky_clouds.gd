extends Node

## Moves the sky's clouds with the wind and sets how much cloud there is, every frame.
##
## Separate from environment.gd's ten-second pass because wind and cloud cover change much faster than the
## hour. The colours the clouds are lit in follow the hour, so those stay in that pass.
##
## The deck drifts exactly as the cloud shadows on the ground do - same wind, same multiplier, same altitude -
## so the clouds overhead and their shadows travel together.

## How much cirrus a clear sky gets. A closing deck hides it.
@export_range(0, 1, 0.01) var cirrus_amount: float = 0.5

var _weather: Node = null

## Metres the wind has carried the deck. Grows without bound; the shader only uses it modulo a noise tile.
var _drift := Vector2.ZERO

@onready var _sky: ShaderMaterial = ($"../WorldEnvironment" as WorldEnvironment).environment.sky.sky_material
@onready var _shadows: Node = $"../CloudShadows"


func _ready() -> void:
	_weather = ConnectionManager.weather
	if _weather == null:
		push_warning("[clouds] no WeatherState; the sky keeps its authored clouds.")
		set_process(false)
		return

	_sky.set_shader_parameter("cloud_altitude", _shadows.cloud_altitude)


func _process(delta: float) -> void:
	var wind: Vector3 = _weather.Wind
	_drift += Vector2(wind.x, wind.z) * _shadows.wind_multiplier * delta

	var overcast: float = _weather.Overcast
	var diffusion: float = _weather.Diffusion

	_sky.set_shader_parameter("cloud_offset", _drift)
	_sky.set_shader_parameter("cloud_coverage", overcast)
	_sky.set_shader_parameter("cloud_thickness", diffusion)
	_sky.set_shader_parameter("cirrus_amount", cirrus_amount * (1.0 - smoothstep(0.6, 0.95, overcast)))
