extends PanelContainer

## The weather and the compass, top centre of the HUD.
##
## Two readings that belong together: the wind is drawn on the same strip as north, so "north-westerly" and
## "north-west of here" are one glance rather than two.
##
## Visible only once there is a camera to take a heading from. The camera is instantiated at runtime under the
## owned entity, so before the player is standing somewhere there is nothing to point at - and a compass that
## defaults to north while the master-select screen is up is not a compass at rest, it is a wrong one. The
## first WeatherSMSG lands within a frame or two of that same moment, so one test covers both.
##
## Polled rather than driven by a signal, because [code]WeatherState[/code] has none: its values are smoothed
## per frame, so a signal would fire every frame and save nobody anything. The same reason
## [code]environment.gd[/code] polls it.

## Kind names, indexed by [code]WeatherSMSG.Kind[/code]. Hardcoded here rather than translated for the reason
## [code]clock.gd[/code] hardcodes its season names: [code]general.csv[/code] is for sentences, and what a sky
## is called belongs to whatever prints it.
const KIND_NAMES := [
	"Clear", "Cloudy", "Fog", "Rain", "Heavy Rain", "Thunderstorm",
	"Snow", "Blizzard", "Sandstorm", "Mana Storm", "Tornado",
]

## Cool, against a strip that is otherwise entirely warm: the wind is the one mark on it that is weather
## rather than geometry.
const _WIND_COLOUR := Color(0.55, 0.78, 0.95)

## Below this the wind has no direction worth drawing, in metres per second. Without it a dead calm points the
## marker due north - [code]atan2(0, 0)[/code] is zero - and the readout says "N 0 m/s", which reads as a
## measurement rather than as the absence of one.
const _CALM_SPEED := 0.5

## Wind bearing change too small to rebuild the mark for, in radians - about half a degree. The wind takes
## twelve seconds to swing round, so nothing is lost, and this is what keeps the widget from allocating a
## dictionary every frame.
const _WIND_EPSILON := 0.0087

@onready var _compass: CompassStrip = $Margin/Rows/Compass
@onready var _temperature_label: Label = $Margin/Rows/Bottom/Temp
@onready var _sky_label: Label = $Margin/Rows/Bottom/Right/Sky
@onready var _wind_label: Label = $Margin/Rows/Bottom/Right/Wind

var _weather: Node = null

## The bearing the wind mark was last built at, or [constant NAN] while it is calm.
var _wind_bearing: float = NAN


func _ready() -> void:
	visible = false

	_weather = ConnectionManager.weather
	if _weather == null:
		push_warning("Weather: ConnectionManager has no weather state; only the compass will be shown.")


func _process(_delta: float) -> void:
	# A compass with no weather behind it is still a compass, so this hangs off the heading and not off
	# _weather - the same call clock.gd and environment.gd make when their source is missing.
	visible = _compass.has_heading

	if not visible or _weather == null:
		return

	# Landed in typed locals rather than used where they are read: these cross from C# as Variant, and the
	# analyser cannot see through that - see environment.gd, which does the same.
	var kind: int = _weather.Kind
	var celsius: float = _weather.TemperatureCelsius
	var wind: Vector3 = _weather.Wind
	var speed: float = _weather.ReportedWindSpeed

	_temperature_label.text = "%d°C" % roundi(celsius)
	_sky_label.text = KIND_NAMES[kind] if kind >= 0 and kind < KIND_NAMES.size() else "Unknown"

	_update_wind(wind, speed)


## Names and marks the wind by where it blows *from*, the way a forecast does.
##
## [code]WeatherState.Wind[/code] is the direction the air travels, which is the opposite bearing - a
## north-westerly blows towards the south-east. The flip is here and not in the compass, because a bearing is
## a bearing by the time the strip sees one.
##
## The direction comes off the smoothed vector so a turning wind sweeps its marker, while the speed comes off
## the raw reading so the number is the server's and not a look multiplier's.
func _update_wind(wind: Vector3, speed: float) -> void:
	if speed < _CALM_SPEED:
		_wind_label.text = "Calm"

		if not is_nan(_wind_bearing):
			_wind_bearing = NAN
			_compass.clear_marks(&"wind")

		return

	var from := wrapf(CompassStrip.bearing_of(wind) + PI, -PI, PI)

	_wind_label.text = "%s %.0f m/s" % [CompassStrip.name_of(from), speed]

	if not is_nan(_wind_bearing) and absf(wrapf(from - _wind_bearing, -PI, PI)) < _WIND_EPSILON:
		return

	_wind_bearing = from
	_compass.set_marks(&"wind", [{"bearing": from, "colour": _WIND_COLOUR, "label": ""}])
