extends Sprite3D
class_name HealthBar

@export var max_value: int = 100
@export var value: int = 100:
	get:
		return value
	set(_value):
		value = _value
		visible = true
		fade_trigger_timer.start()


@onready var fade_trigger_timer: Timer = $FadeTriggerTimer
@onready var progress_bar: ProgressBar = $SubViewport/Control/ProgressBar


## Trigger the fade of the health bar.
func _on_fade_trigger_timer_timeout() -> void:
	var tween: Tween = create_tween()
	tween.tween_property(get_node("."), "modulate:a", 0, 1)
	await tween.finished
	visible = false


func _ready() -> void:
	visible = false
	progress_bar.value = value
	progress_bar.max_value = max_value
