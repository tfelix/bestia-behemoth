extends RigidBody3D
class_name DamageTag

## Fade timings in seconds. Together with [code]_FADE_OUT[/code] they are the tag's whole lifetime,
## after which it frees itself.
const _FADE_IN := 0.1
const _HOLD := 1.7
const _FADE_OUT := 0.4

## The label starts and ends at this fraction of its peak size, so a tag pops rather than blinks.
const _SMALL := 0.6

## A crit has to land visibly harder than an ordinary hit, without earning its own animation.
const _CRIT_SCALE := 1.35

const _CRIT_COLOUR := Color(1, 0.72, 0.25)
const _HEAL_COLOUR := Color(0.45, 0.95, 0.5)

## Miss and dodge recede rather than announce themselves: nothing happened, and the tag should not
## compete with the hits around it.
const _NOTHING_HAPPENED_COLOUR := Color(0.75, 0.75, 0.78)

var damage_msg: DamageEntitySMSG

@onready var _damage_label: Label3D = $DamageLabel


func _ready() -> void:
	_damage_label.text = _label_text()
	_damage_label.modulate = _text_colour()
	_play_fade()


## The outcome words are hardcoded rather than translated for the reason [code]weather.gd[/code]
## hardcodes its sky names: [code]general.csv[/code] is for sentences, and a single word belongs to
## whatever prints it.
func _label_text() -> String:
	match damage_msg.Type:
		DamageType.MISS:
			return "Miss"
		# Nothing on the server emits DODGE yet; it exists only in the wire enum. Naming it costs one
		# line and keeps an evaded swing from ever reading as a bare "0".
		DamageType.DODGE:
			return "Dodge"
		DamageType.HEAL:
			return "+%d" % damage_msg.Damage
		_:
			return str(damage_msg.Damage)


## A tag is legible for barely two seconds while it arcs away, so the outcome has to carry in the
## colour before the number is read at all.
func _text_colour() -> Color:
	match damage_msg.Type:
		DamageType.CRIT:
			return _CRIT_COLOUR
		DamageType.HEAL:
			return _HEAL_COLOUR
		DamageType.MISS, DamageType.DODGE:
			return _NOTHING_HAPPENED_COLOUR
		_:
			return Color.WHITE


## Owned here rather than by an AnimationPlayer because its tracks pinned [code]modulate[/code] to
## white on every keyframe - the one property the outcome colour needs. Alpha and colour have to
## live in the same place for a crit to be orange and still fade.
##
## One parallel step sequenced by delays, rather than parallel groups either side of an interval:
## re-enabling parallel after a sequential tweener appends to that same step instead of a new one,
## which would fade the tag out while it is still meant to be holding.
func _play_fade() -> void:
	var peak := Vector3.ONE * (_CRIT_SCALE if damage_msg.Type == DamageType.CRIT else 1.0)
	var out_delay := _FADE_IN + _HOLD

	_damage_label.modulate.a = 0.0
	_damage_label.outline_modulate.a = 0.0
	_damage_label.scale = peak * _SMALL

	var tween := create_tween().set_parallel()
	tween.tween_property(_damage_label, "modulate:a", 1.0, _FADE_IN)
	tween.tween_property(_damage_label, "outline_modulate:a", 1.0, _FADE_IN)
	tween.tween_property(_damage_label, "scale", peak, _FADE_IN)
	tween.tween_property(_damage_label, "modulate:a", 0.0, _FADE_OUT).set_delay(out_delay)
	tween.tween_property(_damage_label, "outline_modulate:a", 0.0, _FADE_OUT).set_delay(out_delay)
	tween.tween_property(_damage_label, "scale", peak * _SMALL, _FADE_OUT).set_delay(out_delay)
	tween.tween_callback(queue_free).set_delay(out_delay + _FADE_OUT)
