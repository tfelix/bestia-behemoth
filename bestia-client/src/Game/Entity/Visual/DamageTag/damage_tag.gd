extends RefCounted
class_name DamageTag
## One damage number over its life: what it reads, how it arcs and how it fades. [DamageTagLayer] owns the
## label and advances every live tag once a frame.

## Fade timings in seconds. Together they are the tag's whole life.
const _FADE_IN := 0.1
const _HOLD := 1.7
const _FADE_OUT := 0.4

## The label starts and ends at this fraction of its peak size, so a tag pops rather than blinks.
const _SMALL := 0.6

## A crit has to land visibly harder than an ordinary hit, without earning its own animation.
const _CRIT_SCALE := 1.35

## The flight, in screen heights: offsets scale with the distance to the camera, so the motion reads the
## same at every zoom, like the fixed-size label it moves. A short burst away from the hit, then a slow
## drift, so a tag never doubles back over the younger ones below it.
const _BURST_RISE := 0.07
const _BURST_SIDE := 0.05
## Seconds until the burst has covered two thirds of its way.
const _BURST_TIME := 0.12
## Per second, after the burst.
const _DRIFT := 0.04
## Screen left, where every tag flew before it knew who struck it.
const _SIDE := -1.0

const _CRIT_COLOUR := Color(1, 0.72, 0.25)
const _HEAL_COLOUR := Color(0.45, 0.95, 0.5)

## Miss and dodge recede rather than announce themselves: nothing happened, and the tag should not
## compete with the hits around it.
const _NOTHING_HAPPENED_COLOUR := Color(0.75, 0.75, 0.78)

var label: Label3D

var _type := DamageType.NORMAL
var _start := Vector3.ZERO
var _age := 0.0
var _peak := 1.0


func _init(tag_label: Label3D) -> void:
	label = tag_label


func start(msg: DamageEntitySMSG, at: Vector3, camera: Camera3D) -> void:
	_type = msg.Type
	_start = at
	_peak = _CRIT_SCALE if _type == DamageType.CRIT else 1.0
	label.modulate = _text_colour()
	label.text = _label_text(msg)
	label.show()
	advance(0.0, camera)


## False once the tag has run its course.
func advance(delta: float, camera: Camera3D) -> bool:
	_age += delta
	if _age >= _FADE_IN + _HOLD + _FADE_OUT:
		return false

	var visibility := _visibility_at(_age)
	label.global_position = _start + _flight_offset_at(_age, camera)
	label.scale = Vector3.ONE * _peak * lerpf(_SMALL, 1.0, visibility)
	# Fades through transparency, not modulate: a Label3D rebuilds its mesh on every modulate change.
	label.transparency = 1.0 - visibility
	return true


## 0 to 1 over the fade in, 1 while holding, back to 0 over the fade out.
func _visibility_at(age: float) -> float:
	if age < _FADE_IN:
		return age / _FADE_IN
	var fading_for := age - _FADE_IN - _HOLD
	return 1.0 - clampf(fading_for / _FADE_OUT, 0.0, 1.0)


func _flight_offset_at(age: float, camera: Camera3D) -> Vector3:
	var burst := 1.0 - exp(-age / _BURST_TIME)
	var sideways := _SIDE * _BURST_SIDE * burst
	var upwards := _BURST_RISE * burst + _DRIFT * age
	var screen := camera.global_basis.x * sideways + camera.global_basis.y * upwards
	return screen * _screen_height_at(camera, _start)


## How many world units one screen height spans at [param point].
static func _screen_height_at(camera: Camera3D, point: Vector3) -> float:
	if camera.projection == Camera3D.PROJECTION_ORTHOGONAL:
		return camera.size
	return 2.0 * camera.global_position.distance_to(point) * tan(deg_to_rad(camera.fov) * 0.5)


## The outcome words are hardcoded rather than translated for the reason [code]weather.gd[/code]
## hardcodes its sky names: [code]general.csv[/code] is for sentences, and a single word belongs to
## whatever prints it.
func _label_text(msg: DamageEntitySMSG) -> String:
	match msg.Type:
		DamageType.MISS:
			return "Miss"
		# Nothing on the server emits DODGE yet; it exists only in the wire enum. Naming it costs one
		# line and keeps an evaded swing from ever reading as a bare "0".
		DamageType.DODGE:
			return "Dodge"
		DamageType.HEAL:
			return "+%d" % msg.Damage
		_:
			return str(msg.Damage)


## A tag is legible for barely two seconds while it arcs away, so the outcome has to carry in the
## colour before the number is read at all.
func _text_colour() -> Color:
	match _type:
		DamageType.CRIT:
			return _CRIT_COLOUR
		DamageType.HEAL:
			return _HEAL_COLOUR
		DamageType.MISS, DamageType.DODGE:
			return _NOTHING_HAPPENED_COLOUR
		_:
			return Color.WHITE
