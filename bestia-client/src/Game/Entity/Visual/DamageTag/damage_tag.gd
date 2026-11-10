extends RefCounted
class_name DamageTag
## One damage number over its life: what it reads, how it arcs and how it fades. [DamageTagLayer] owns the
## label and advances every live tag once a frame.

## Whose fight a hit belongs to, seen from this player.
enum Involvement { DEALT, TAKEN, BYSTANDER }

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
const _LINE_HEIGHT := 0.035
## A hit grows with the share of the target's health it took: 1x at a tenth, these at the ends.
const _SMALLEST_IMPACT := 0.85
const _LARGEST_IMPACT := 1.4
const _IMPACT_GROWTH := 0.57
## Strangers' fights stay in the background, so the player's own numbers carry.
const _BYSTANDER_SCALE := 0.75
const _BYSTANDER_ALPHA := 0.7
const _MISS_SCALE := 0.8
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
const _BURST_SIDE := 0.085
## A hit starts this far to its side already, so it never lands in the middle column of heals and misses.
const _HIT_START_SIDE := 0.035
## Seconds until the burst has covered two thirds of its way.
const _BURST_TIME := 0.12
## Per second, after the burst.
const _DRIFT := 0.04
## Every outcome takes this flight: a younger tag pushes the older ones on its side up a line, and one
## that rose faster or slower would cross into them. Jitter varies it only a little, so a flurry does
## not trace one line, and only sideways, where it cannot close the gap to the tag above.
const _BURST_JITTER := 0.15
const _START_JITTER := 0.012
## Heals and misses skip the burst and the pop: they rise straight up at this many screen heights a second,
## in a column of their own between those of the hits. Quickly, to clear the spot where every hit starts.
const _PLAIN_RISE := 0.2
## A tag pushed up by a younger one glides there over about this many seconds.
const _PUSH_TIME := 0.05

const _CRIT_COLOUR := Color(1, 0.72, 0.25)
const _HEAL_COLOUR := Color(0.45, 0.95, 0.5)
## Damage to the player's own entities reads as a warning.
const _TAKEN_COLOUR := Color(1, 0.36, 0.3)
const _TAKEN_CRIT_COLOUR := Color(1, 0.16, 0.12)

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
var _alpha := 1.0
var _colour := Color.WHITE
var involvement := Involvement.DEALT
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


## [param max_hp] is 0 when the target's health is unknown here.
func start(
	msg: DamageEntitySMSG, at: Vector3, fly_side: float, max_hp: int, whose: Involvement, camera: Camera3D
) -> void:
	_type = msg.Type
	target_id = msg.EntityId
	involvement = whose
	_start = at
	side = fly_side if is_hit(_type) else 0.0
	_pushed = 0.0
	_pushed_shown = 0.0
	_burst = Vector2(_BURST_SIDE * _jitter(), _BURST_RISE)
	_start_shift = randf_range(-_START_JITTER, _START_JITTER) if is_hit(_type) else 0.0
	_age = 0.0
	_life = _life_of(_type)
	_peak = _size_of(msg, max_hp)
	_pop = _pop_of(_type)
	_alpha = _BYSTANDER_ALPHA if whose == Involvement.BYSTANDER else 1.0
	_colour = _text_colour()
	_paint(Color.WHITE if _type == DamageType.CRIT else _colour)
	label.text = _label_text(msg)
	# Above strangers' numbers where they cross; the outline stays below its own text.
	label.render_priority = 0 if whose == Involvement.BYSTANDER else 2
	label.outline_render_priority = label.render_priority - 1
	label.show()
	advance(0.0, camera)


## Moves this tag up by [param height] screen heights, to make room for a younger one below.
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
	label.transparency = 1.0 - _alpha * (1.0 - fade * fade)
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


## A hit bursts away from its attacker and pops; heals and misses only rise.
static func is_hit(type: int) -> bool:
	return type == DamageType.NORMAL or type == DamageType.CRIT


func _flight_offset_at(age: float, camera: Camera3D) -> Vector3:
	var sideways := 0.0
	var upwards := _pushed_shown + _PLAIN_RISE * age
	if is_hit(_type):
		var burst := 1.0 - exp(-age / _BURST_TIME)
		sideways = _start_shift + side * lerpf(_HIT_START_SIDE, _burst.x, burst) + _shake_at(age)
		upwards = _pushed_shown + _burst.y * burst + _DRIFT * age
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


func _size_of(msg: DamageEntitySMSG, max_hp: int) -> float:
	var size := _CRIT_SCALE if _type == DamageType.CRIT else 1.0
	if _type == DamageType.MISS or _type == DamageType.DODGE or _type == DamageType.BLOCK:
		size = _MISS_SCALE
	elif max_hp > 0 and msg.Damage > 0:
		var share := float(msg.Damage) / max_hp
		size *= clampf(1.0 + _IMPACT_GROWTH * log(share * 10.0) / log(10.0), _SMALLEST_IMPACT, _LARGEST_IMPACT)
	if involvement == Involvement.BYSTANDER:
		size *= _BYSTANDER_SCALE
	return size


static func _life_of(type: int) -> float:
	match type:
		DamageType.CRIT:
			return _CRIT_LIFE
		DamageType.HEAL:
			return _HEAL_LIFE
		DamageType.MISS, DamageType.DODGE, DamageType.BLOCK:
			return _MISS_LIFE
		_:
			return _LIFE


static func _pop_of(type: int) -> float:
	match type:
		DamageType.CRIT:
			return _CRIT_POP
		DamageType.NORMAL:
			return _POP
		_:
			return 1.0


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
		DamageType.BLOCK:
			return "Block"
		DamageType.HEAL:
			return "+%d" % msg.Damage
		_:
			return str(msg.Damage)


## A tag is legible for barely a second while it flies off, so the outcome has to carry in the
## colour before the number is read at all.
func _text_colour() -> Color:
	var taken := involvement == Involvement.TAKEN
	match _type:
		DamageType.CRIT:
			return _TAKEN_CRIT_COLOUR if taken else _CRIT_COLOUR
		DamageType.NORMAL:
			return _TAKEN_COLOUR if taken else Color.WHITE
		DamageType.HEAL:
			return _HEAL_COLOUR
		DamageType.MISS, DamageType.DODGE, DamageType.BLOCK:
			return _NOTHING_HAPPENED_COLOUR
		_:
			return Color.WHITE
