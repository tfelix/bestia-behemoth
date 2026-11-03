extends RefCounted
class_name DamageTag
## One damage number over its life: what it reads, how it arcs and how it fades. [DamageTagLayer] owns the
## label and advances every live tag once a frame.

## Lives in seconds. A crit stays longer to be read; a miss is gone before it competes with anything.
const _LIFE := 0.9
const _CRIT_LIFE := 1.3
const _HEAL_LIFE := 1.1
const _MISS_LIFE := 0.6
## The share of its life after which a tag fades.
const _FADE_FROM := 0.7
## What is left of the size when the fade ends.
const _FADED_SCALE := 0.8

## A hit lands oversized and snaps to its size, undershooting a little on the way. Shown at full alpha
## from the first frame: fading in is what made the old tags feel soft.
const _POP_TIME := 0.12
const _POP := 1.6
const _CRIT_POP := 2.4

## A crit has to land visibly harder than an ordinary hit.
const _CRIT_SCALE := 1.35
## The height of one plain number, in screen heights, at the label's font size.
const _LINE_HEIGHT := 0.045
## A crit starts white and turns to its colour, and jolts sideways for a few frames.
const _CRIT_FLASH_TIME := 0.12
const _CRIT_SHAKE_TIME := 0.15
const _CRIT_SHAKE := 0.006
const _CRIT_SHAKE_SPEED := 120.0

## The outline is the fill colour, nearly black: a hint of the hue keeps a crit warm at its edge.
const _OUTLINE_DARKEN := 0.88

## The flight, in screen heights: offsets scale with the distance to the camera, so the motion reads the
## same at every zoom, like the fixed-size label it moves. A short burst away from the hit, then a slow
## drift, so a tag never doubles back over the younger ones below it.
const _BURST_RISE := 0.07
const _BURST_SIDE := 0.07
## Seconds until the burst has covered two thirds of its way.
const _BURST_TIME := 0.12
## Per second, after the burst.
const _DRIFT := 0.04
## No two hits fly quite alike, so a flurry spreads out instead of tracing one line.
const _BURST_JITTER := 0.15
const _START_JITTER := 0.012
## A tag pushed up by a younger one glides there over about this many seconds.
const _PUSH_TIME := 0.05

const _CRIT_COLOUR := Color(1, 0.72, 0.25)
const _HEAL_COLOUR := Color(0.45, 0.95, 0.5)

## Miss and dodge recede rather than announce themselves: nothing happened, and the tag should not
## compete with the hits around it.
const _NOTHING_HAPPENED_COLOUR := Color(0.75, 0.75, 0.78)

var label: Label3D

var _type := DamageType.NORMAL
var _start := Vector3.ZERO
var _age := 0.0
var _life := _LIFE
var _peak := 1.0
var _pop := _POP
var _colour := Color.WHITE
var target_id := 0
## -1 flies to screen left, 1 to the right.
var side := 1.0
## Screen heights younger tags have pushed this one up, and how far it has got there.
var _pushed := 0.0
var _pushed_shown := 0.0
var _burst := Vector2(_BURST_SIDE, _BURST_RISE)
var _start_shift := 0.0


func _init(tag_label: Label3D) -> void:
	label = tag_label


func start(msg: DamageEntitySMSG, at: Vector3, fly_side: float, camera: Camera3D) -> void:
	_type = msg.Type
	target_id = msg.EntityId
	_start = at
	side = fly_side
	_pushed = 0.0
	_pushed_shown = 0.0
	_burst = Vector2(_BURST_SIDE, _BURST_RISE) * Vector2(_jitter(), _jitter())
	_start_shift = randf_range(-_START_JITTER, _START_JITTER)
	_age = 0.0
	_life = _life_of(_type)
	_peak = _CRIT_SCALE if _type == DamageType.CRIT else 1.0
	_pop = _pop_of(_type)
	_colour = _text_colour()
	_paint(Color.WHITE if _type == DamageType.CRIT else _colour)
	label.text = _label_text(msg)
	label.show()
	advance(0.0, camera)


## Makes room above the hit for a younger tag of [param height] screen heights.
func push_up(height: float) -> void:
	_pushed += height


## How much room this tag takes above the hit, in screen heights.
func line_height() -> float:
	return _LINE_HEIGHT * _peak


## False once the tag has run its course.
func advance(delta: float, camera: Camera3D) -> bool:
	_age += delta
	if _age >= _life:
		return false

	_pushed_shown = lerpf(_pushed, _pushed_shown, exp(-delta / _PUSH_TIME))
	var fade := clampf((_age / _life - _FADE_FROM) / (1.0 - _FADE_FROM), 0.0, 1.0)
	label.global_position = _start + _flight_offset_at(_age, camera)
	label.scale = Vector3.ONE * _peak * _pop_scale_at(_age) * lerpf(1.0, _FADED_SCALE, fade)
	# Fades through transparency, not modulate: a Label3D rebuilds its mesh on every modulate change.
	label.transparency = fade * fade
	if _type == DamageType.CRIT and _age < _CRIT_FLASH_TIME + delta:
		_paint(Color.WHITE.lerp(_colour, minf(_age / _CRIT_FLASH_TIME, 1.0)))
	return true


func _pop_scale_at(age: float) -> float:
	if age >= _POP_TIME:
		return 1.0
	return Tween.interpolate_value(_pop, 1.0 - _pop, age, _POP_TIME, Tween.TRANS_BACK, Tween.EASE_OUT)


func _paint(colour: Color) -> void:
	label.modulate = colour
	label.outline_modulate = colour.darkened(_OUTLINE_DARKEN)


func _flight_offset_at(age: float, camera: Camera3D) -> Vector3:
	var burst := 1.0 - exp(-age / _BURST_TIME)
	var sideways := _start_shift + side * _burst.x * burst + _shake_at(age)
	var upwards := _pushed_shown + _burst.y * burst + _DRIFT * age
	var screen := camera.global_basis.x * sideways + camera.global_basis.y * upwards
	return screen * _screen_height_at(camera, _start)


static func _jitter() -> float:
	return randf_range(1.0 - _BURST_JITTER, 1.0 + _BURST_JITTER)


func _shake_at(age: float) -> float:
	if _type != DamageType.CRIT or age >= _CRIT_SHAKE_TIME:
		return 0.0
	return sin(age * _CRIT_SHAKE_SPEED) * _CRIT_SHAKE * (1.0 - age / _CRIT_SHAKE_TIME)


## How many world units one screen height spans at [param point].
static func _screen_height_at(camera: Camera3D, point: Vector3) -> float:
	if camera.projection == Camera3D.PROJECTION_ORTHOGONAL:
		return camera.size
	return 2.0 * camera.global_position.distance_to(point) * tan(deg_to_rad(camera.fov) * 0.5)


static func _life_of(type: int) -> float:
	match type:
		DamageType.CRIT:
			return _CRIT_LIFE
		DamageType.HEAL:
			return _HEAL_LIFE
		DamageType.MISS, DamageType.DODGE:
			return _MISS_LIFE
		_:
			return _LIFE


## Nothing landed on a miss, so nothing pops.
static func _pop_of(type: int) -> float:
	match type:
		DamageType.CRIT:
			return _CRIT_POP
		DamageType.MISS, DamageType.DODGE:
			return 1.0
		_:
			return _POP


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


## A tag is legible for barely a second while it flies off, so the outcome has to carry in the
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
