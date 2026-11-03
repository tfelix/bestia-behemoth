extends Node3D
class_name ArrowTracer

## An arrow in flight from a shooter to what it hit, purely for show: the server has already resolved the hit
## and the damage tag waits for [signal arrived].

signal arrived()

## Tiles per second. Fast enough that a shot never trails behind the next one at the quickest attack speed.
const SPEED := 22.0
## How high the arc rises per tile flown, so a long shot visibly lobs and a short one stays flat.
const ARC_PER_TILE := 0.04

var _from: Vector3
var _to: Vector3
var _flight := 0.0
var _elapsed := 0.0


## Starts the flight. The arrow points along its path by looking ahead of itself each frame.
func fly(from: Vector3, to: Vector3) -> void:
	_from = from
	_to = to
	_flight = maxf(from.distance_to(to) / SPEED, 0.05)
	position = from


func flight_seconds() -> float:
	return _flight


func _process(delta: float) -> void:
	_elapsed += delta
	var t := minf(_elapsed / _flight, 1.0)
	var next := _point_at(minf(t + 0.05, 1.0))
	position = _point_at(t)
	if not position.is_equal_approx(next):
		look_at(next, Vector3.UP)

	if t >= 1.0:
		arrived.emit()
		queue_free()


func _point_at(t: float) -> Vector3:
	var arc := 4.0 * t * (1.0 - t) * _from.distance_to(_to) * ARC_PER_TILE
	return _from.lerp(_to, t) + Vector3.UP * arc
