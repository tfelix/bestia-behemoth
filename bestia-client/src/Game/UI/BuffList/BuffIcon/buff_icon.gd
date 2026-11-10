extends VBoxContainer
## A single buff/debuff icon. Ticks its own remaining duration down between server
## updates (the server only re-syncs the buff list when it changes, not every
## tick - see StatusEffects.kt/StatusEffectDurationSystem.kt), and reflects it in DurationLabel
## the way WoW does under its buffs: hours ("2 h") or minutes ("13 min") rounded up, then
## seconds ("42 s") once under a minute, slowly pulsing to warn the buff is about to expire.

const _MINUTE_THRESHOLD_SECONDS: float = 60.0
const _HOUR_THRESHOLD_SECONDS: float = 3600.0
const _FADE_MIN_ALPHA: float = 0.3
const _FADE_HALF_PERIOD_SECONDS: float = 0.9

@onready var _button: TextureButton = $BuffIconButton
@onready var _duration_label: Label = $DurationLabel

var _remaining_seconds: float = 0.0
var _fade_tween: Tween = null
var _effect_id: int = 0
var _is_debuff: bool = false


func _ready() -> void:
	_button.gui_input.connect(_on_button_input)


func setup(entry: StatusEffectListEntry) -> void:
	_effect_id = entry.EffectId
	_is_debuff = entry.Debuff
	_button.modulate = Color(1.0, 0.55, 0.55) if entry.Debuff else Color(1.0, 1.0, 1.0)
	_button.tooltip_text = _description_of(entry.EffectId)
	_remaining_seconds = entry.RemainingSeconds

	# Infinite for an effect held until the server takes it away, such as a ward: there is no clock to show.
	_duration_label.visible = not is_inf(_remaining_seconds)
	if _duration_label.visible:
		_refresh()


func _process(delta: float) -> void:
	if _remaining_seconds <= 0.0 or is_inf(_remaining_seconds):
		return
	_remaining_seconds = maxf(_remaining_seconds - delta, 0.0)
	_refresh()


func _refresh() -> void:
	if _remaining_seconds > _HOUR_THRESHOLD_SECONDS:
		_duration_label.text = "%d h" % int(ceil(_remaining_seconds / _HOUR_THRESHOLD_SECONDS))
		_set_fading(false)
	elif _remaining_seconds > _MINUTE_THRESHOLD_SECONDS:
		_duration_label.text = "%d min" % int(ceil(_remaining_seconds / _MINUTE_THRESHOLD_SECONDS))
		_set_fading(false)
	else:
		_duration_label.text = "%d s" % int(ceil(_remaining_seconds))
		_set_fading(true)


## A right-click drops a buff, the way a toggle skill's recast does. A debuff is not the player's to end, so the
## click does nothing there; the server checks the same and also refuses buffs that must run their course.
func _on_button_input(event: InputEvent) -> void:
	var click := event as InputEventMouseButton
	if click == null or not click.pressed or click.button_index != MOUSE_BUTTON_RIGHT or _is_debuff:
		return
	accept_event()
	if ConnectionManager.is_ready_to_send():
		ConnectionManager.cancel_status_effect(_effect_id)


## Empty for an effect with no description yet, so the icon shows no tooltip rather than a raw key.
func _description_of(effect_id: int) -> String:
	var key := "STATUS_EFFECT_%d" % effect_id
	var text := tr(key)
	return "" if text == key else text


func _set_fading(should_fade: bool) -> void:
	if should_fade == (_fade_tween != null):
		return

	if should_fade:
		_fade_tween = create_tween().set_loops()
		_fade_tween.tween_property(_duration_label, "modulate:a", _FADE_MIN_ALPHA, _FADE_HALF_PERIOD_SECONDS).set_trans(Tween.TRANS_SINE)
		_fade_tween.tween_property(_duration_label, "modulate:a", 1.0, _FADE_HALF_PERIOD_SECONDS).set_trans(Tween.TRANS_SINE)
	else:
		_fade_tween.kill()
		_fade_tween = null
		_duration_label.modulate.a = 1.0
