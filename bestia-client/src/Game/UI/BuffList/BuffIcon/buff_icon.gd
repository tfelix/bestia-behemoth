extends VBoxContainer
## A single buff/debuff icon. Ticks its own remaining duration down between server
## updates (the server only re-syncs the buff list when it changes, not every
## tick - see StatusEffects.kt/BuffDurationSystem.kt), and reflects it in DurationLabel:
## minutes ("2m") while there's more than a minute left, a plain countdown of
## seconds once under a minute, slowly pulsing to warn the buff is about to expire.

const _MINUTE_THRESHOLD_SECONDS: float = 60.0
const _FADE_MIN_ALPHA: float = 0.3
const _FADE_HALF_PERIOD_SECONDS: float = 0.9

@onready var _button: TextureButton = $BuffIconButton
@onready var _duration_label: Label = $DurationLabel

var _remaining_seconds: float = 0.0
var _fade_tween: Tween = null


func setup(entry: BuffListEntry) -> void:
	_button.modulate = Color(1.0, 0.55, 0.55) if entry.Debuff else Color(1.0, 1.0, 1.0)
	_remaining_seconds = entry.RemainingSeconds
	_refresh()


func _process(delta: float) -> void:
	if _remaining_seconds <= 0.0:
		return
	_remaining_seconds = maxf(_remaining_seconds - delta, 0.0)
	_refresh()


func _refresh() -> void:
	if _remaining_seconds > _MINUTE_THRESHOLD_SECONDS:
		_duration_label.text = "%dm" % int(ceil(_remaining_seconds / 60.0))
		_set_fading(false)
	else:
		_duration_label.text = "%d" % int(ceil(_remaining_seconds))
		_set_fading(true)


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
