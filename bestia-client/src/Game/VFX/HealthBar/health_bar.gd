extends Sprite3D
class_name HealthBar

var _max_value: int = 100
var _value: int = 100

@onready var _fade_trigger_timer: Timer = $FadeTriggerTimer
@onready var _progress_bar: ProgressBar = $SubViewport/Control/ProgressBar


func update_health(msg: HealthComponentSMSG) -> void:
	if _progress_bar != null:
		visible = true
		_fade_trigger_timer.start()
		_progress_bar.max_value = msg.Max
		_progress_bar.value = msg.Current
	else:
		_max_value = msg.Max
		_value = msg.Current


## Trigger the fade of the health bar.
func _on_fade_trigger_timer_timeout() -> void:
	var tween: Tween = create_tween()
	tween.tween_property(get_node("."), "modulate:a", 0, 1)
	await tween.finished
	visible = false


func _ready() -> void:
	visible = false
	# we set the initial value here when the progress bar itself if loaded
	# via onready.
	_progress_bar.value = _value
	_progress_bar.max_value = _max_value
