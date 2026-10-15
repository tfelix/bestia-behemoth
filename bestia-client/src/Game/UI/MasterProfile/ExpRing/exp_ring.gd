extends Range
## Exp towards the next level, drawn as a ring that fills clockwise from twelve o'clock.

const _TOP := -PI / 2.0
const _POINTS_PER_CIRCLE := 64

@export var ring_width := 3.5
@export var track_color := Color(0.08, 0.08, 0.08, 0.75)
@export var fill_color := Color(0.95, 0.96, 1.0, 0.92)


func _ready() -> void:
	value_changed.connect(func(_value: float) -> void: queue_redraw())
	changed.connect(queue_redraw)


func _draw() -> void:
	var centre := size / 2.0
	var radius := minf(size.x, size.y) / 2.0 - ring_width / 2.0
	draw_arc(centre, radius, 0.0, TAU, _POINTS_PER_CIRCLE, track_color, ring_width, true)

	if ratio <= 0.0:
		return
	var points := maxi(2, ceili(_POINTS_PER_CIRCLE * ratio))
	draw_arc(centre, radius, _TOP, _TOP + TAU * ratio, points, fill_color, ring_width, true)
