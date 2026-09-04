extends Sprite3D
class_name HealthBar

## Green bar above an entity's head, shown only for a while after its health changed.
##
## Health is a synced component, so the server pushes the full pool once as soon as the entity
## enters view. That first push is only a seed - it carries no news - and must leave the bar
## hidden, otherwise every bestia already standing around at login would greet the player with
## its health bar up until the fade timer ran out. Only a later push that actually moves the
## pool (damage, healing, a max change) pops the bar open and restarts the fade.

var _max_value: int = 100
var _value: int = 100
var _seeded: bool = false
var _fade_tween: Tween

@onready var _fade_trigger_timer: Timer = $FadeTriggerTimer
@onready var _progress_bar: ProgressBar = $SubViewport/Control/ProgressBar


func update_health(msg: HealthComponentSMSG) -> void:
	var changed := _seeded and (msg.Current != _value or msg.Max != _max_value)
	_value = msg.Current
	_max_value = msg.Max
	_seeded = true

	if _progress_bar != null:
		_progress_bar.max_value = _max_value
		_progress_bar.value = _value

	if changed:
		_show_until_fade()


func _show_until_fade() -> void:
	# A fade that has already run left modulate.a at 0, and one still running would drag it back
	# down, so both have to be undone before showing the bar again - otherwise it would be
	# "visible" while fully transparent.
	if _fade_tween != null:
		_fade_tween.kill()
		_fade_tween = null
	modulate.a = 1.0
	visible = true
	_fade_trigger_timer.start()


## Trigger the fade of the health bar.
func _on_fade_trigger_timer_timeout() -> void:
	var tween := create_tween()
	_fade_tween = tween
	tween.tween_property(self, "modulate:a", 0.0, 1.0)
	await tween.finished
	# Health that changed again mid-fade replaced this tween; only the fade that is still the
	# current one may hide the bar.
	if _fade_tween == tween:
		_fade_tween = null
		visible = false


func _ready() -> void:
	visible = false
	# we set the initial value here when the progress bar itself if loaded
	# via onready.
	_progress_bar.value = _value
	_progress_bar.max_value = _max_value
