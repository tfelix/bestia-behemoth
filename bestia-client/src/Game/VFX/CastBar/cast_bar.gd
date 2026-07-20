extends Sprite3D
class_name CastBar

## White bar above an entity's head that fills up while it channels a skill.
##
## The server broadcasts CastingComponentSMSG with the remaining and total cast time; between those
## updates the bar ticks down locally in _process so the fill stays smooth regardless of how often
## the server re-syncs. The cast ending — completed *or* interrupted — arrives as a
## ComponentRemovedSMSG for Casting and simply hides the bar again.

var _total_seconds: float = 0.0
var _remaining_seconds: float = 0.0
var _casting: bool = false

@onready var _progress_bar: ProgressBar = $SubViewport/Control/ProgressBar


func update_casting(msg: CastingComponentSMSG) -> void:
	_total_seconds = msg.TotalSeconds
	_remaining_seconds = msg.RemainingSeconds
	_casting = _total_seconds > 0.0 and _remaining_seconds > 0.0
	visible = _casting
	_refresh()


## Called when the Casting component is removed, i.e. the cast finished or was interrupted.
func clear_casting() -> void:
	_casting = false
	_remaining_seconds = 0.0
	visible = false


func is_casting() -> bool:
	return _casting


func _process(delta: float) -> void:
	if not _casting:
		return

	_remaining_seconds -= delta
	if _remaining_seconds <= 0.0:
		# Hold the bar full until the server confirms the end of the cast, rather than guessing.
		_remaining_seconds = 0.0

	_refresh()


func _refresh() -> void:
	if _progress_bar == null or _total_seconds <= 0.0:
		return

	var elapsed := _total_seconds - _remaining_seconds
	_progress_bar.max_value = _total_seconds
	_progress_bar.value = clampf(elapsed, 0.0, _total_seconds)


func _ready() -> void:
	visible = false
	_progress_bar.value = 0.0
