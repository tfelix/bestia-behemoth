extends PanelContainer
## The in-game date and time, top right of the HUD.
##
## Reads ConnectionManager.world_clock, which anchors itself to WorldInfoSMSG and runs forward locally -
## so this updates once per in-game minute (about every twenty real seconds) rather than every frame,
## and there is no per-tick traffic behind it.
##
## Starts hidden and stays hidden against a server that sends no calendar, which is how an older
## zone-server presents itself. A clock stuck at Year 1 Day 1 reads as a broken world rather than as a
## missing feature.

## Season ordinals as WorldClock emits them. Named here rather than in C# because what a season is called
## belongs to whatever displays it; which season it *is* belongs to the calendar.
const SEASON_NAMES := ["Spring", "Summer", "Fall", "Winter"]

@onready var _time_label: Label = $Margin/Rows/TopRow/Time
@onready var _phase_label: Label = $Margin/Rows/TopRow/Phase
@onready var _date_label: Label = $Margin/Rows/Date

## Warm by day, cool by night. The only thing distinguishing 03:00 from 15:00 at a glance.
const DAY_COLOUR := Color(1.0, 0.94, 0.78)
const NIGHT_COLOUR := Color(0.72, 0.80, 1.0)

## The clock, kept so the phase can be read from the light level rather than from the is_night flag.
## Four names and not two: the twilight hours are the interesting ones, and calling 21:00 "Day" when the
## sky is visibly orange reads as the widget being wrong.
var _clock: Node = null


func _ready() -> void:
	visible = false

	var clock: Node = ConnectionManager.world_clock
	if clock == null:
		push_warning("Clock: ConnectionManager has no world_clock; no in-game time will be shown.")
		return

	_clock = clock
	clock.TimeChanged.connect(_on_time_changed)

	# The world info arrived during authentication, long before this HUD existed, so ask for the current
	# reading rather than waiting for the next minute to roll over.
	clock.PublishNow()


func _on_time_changed(
	year: int, month: int, day: int, hour: int, minute: int, season: int, is_night: bool
) -> void:
	visible = true

	_time_label.text = "%02d:%02d" % [hour, minute]
	_time_label.add_theme_color_override("font_color", NIGHT_COLOUR if is_night else DAY_COLOUR)

	_phase_label.text = _phase_name(hour, is_night)

	var season_name: String = SEASON_NAMES[season] if season >= 0 and season < SEASON_NAMES.size() else "Month %d" % month
	_date_label.text = "%s · Day %d · Year %d" % [season_name, day, year]


## Which quarter of the day it is, from the same light level the world is lit by.
##
## Dawn and dusk are told apart by the hour rather than by the light, because the ramps are symmetric —
## a half-lit sky is the same number going up as coming down, and only the clock knows which.
func _phase_name(hour: int, is_night: bool) -> String:
	if is_night:
		return "Night"

	if _clock == null or _clock.Daylight >= 1.0:
		return "Day"

	return "Dawn" if hour < 12 else "Dusk"
