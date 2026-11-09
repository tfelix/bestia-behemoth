extends Node3D

## A roadside lantern's everflame: a pale magic fire that burns brighter as the day goes.
##
## Presentation only, and driven by the clock rather than by the server: the lanterns are lit for everyone at
## the same hour, so there is no state to send. By day the flame is a faint glow in its cage; from dusk it
## brightens and the light comes on. The light carries its own distance fade (set on the node), which is the
## budget: only the lanterns near the camera cost a light at all, however many line the road.

const _FLICKER_SPEED := 4.0
const _FLICKER_DEPTH := 0.06
## Daylight at and above which the flame is at its day glow, and at and below which it is fully lit.
const _DAY := 0.55
const _NIGHT := 0.2
const _LIGHT_ENERGY := 1.6
const _DAY_GLOW := 0.4
const _NIGHT_GLOW := 3.5
## Seconds between clock reads: the light follows dusk, which takes minutes, so a frame-rate read is waste.
const _READ_INTERVAL := 0.5

@onready var _light: OmniLight3D = $Light
@onready var _flame: MeshInstance3D = $Flame

var _material: StandardMaterial3D = null
var _night := 0.0
var _since_read := _READ_INTERVAL
## Per-lantern phase, so a row of them does not flicker in step.
var _elapsed := 0.0


func _ready() -> void:
	_elapsed = randf() * 10.0
	var base := _flame.get_surface_override_material(0) as StandardMaterial3D
	if base != null:
		_material = base.duplicate() as StandardMaterial3D
		_flame.set_surface_override_material(0, _material)


func _process(delta: float) -> void:
	_elapsed += delta
	_since_read += delta
	if _since_read >= _READ_INTERVAL:
		_since_read = 0.0
		_night = _night_amount()

	var flicker := 1.0 + (sin(_elapsed * _FLICKER_SPEED) + 0.5 * sin(_elapsed * _FLICKER_SPEED * 2.7)) * _FLICKER_DEPTH
	_light.visible = _night > 0.01
	_light.light_energy = _LIGHT_ENERGY * _night * flicker
	if _material != null:
		_material.emission_energy_multiplier = lerpf(_DAY_GLOW, _NIGHT_GLOW, _night) * flicker


## How lit the lantern is, from 0 by day to 1 at night, off the shared world clock.
func _night_amount() -> float:
	var clock: Node = ConnectionManager.world_clock
	if clock == null or not clock.IsAnchored():
		return 0.0
	var daylight: float = clock.Daylight
	return clampf((_DAY - daylight) / (_DAY - _NIGHT), 0.0, 1.0)
