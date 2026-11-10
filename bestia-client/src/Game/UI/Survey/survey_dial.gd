class_name SurveyDial
extends Control

## The theodolite's half circle: the target arc of the current round and the swinging needle. Angles are degrees
## from straight up, -90 (left) to +90 (right), as the server counts them.

const _ARC_COLOUR := Color(0.45, 0.85, 0.45, 0.85)
const _RIM_COLOUR := Color(0.60, 0.60, 0.66)
const _NEEDLE_COLOUR := Color(1.0, 0.82, 0.35)

var needle_degrees := 0.0:
	set(value):
		needle_degrees = value
		queue_redraw()

var arc_centre_degrees := 0.0
var arc_width_degrees := 0.0


func _draw() -> void:
	var pivot := Vector2(size.x / 2.0, size.y - 8.0)
	var radius: float = min(size.x / 2.0, size.y) - 12.0

	draw_arc(pivot, radius, PI, TAU, 64, _RIM_COLOUR, 2.0, true)
	var from := _to_canvas(arc_centre_degrees - arc_width_degrees / 2.0)
	var to := _to_canvas(arc_centre_degrees + arc_width_degrees / 2.0)
	draw_arc(pivot, radius - 6.0, from, to, 24, _ARC_COLOUR, 10.0, true)
	draw_line(pivot, pivot + Vector2.from_angle(_to_canvas(needle_degrees)) * radius, _NEEDLE_COLOUR, 3.0, true)
	draw_circle(pivot, 5.0, _NEEDLE_COLOUR)


## Server degrees to a canvas angle: 0 is straight up, which on the canvas is -PI/2.
func _to_canvas(degrees: float) -> float:
	return deg_to_rad(degrees) - PI / 2.0
