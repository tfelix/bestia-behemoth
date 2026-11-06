extends Sprite3D
class_name CountdownRing

## A ring over a ground item that empties while time runs out, like the cast bar fills. The server sends the time
## left and the whole time; between those updates the ring counts down here.

const _RING_WIDTH := 5.0

var _total_seconds: float = 0.0
var _remaining_seconds: float = 0.0

@export var color: Color = Color(0.95, 0.8, 0.35)

@onready var _canvas: Control = $SubViewport/Canvas


func _ready() -> void:
	visible = false
	_canvas.draw.connect(_draw_ring)


func start(remaining_seconds: float, total_seconds: float) -> void:
	_remaining_seconds = remaining_seconds
	_total_seconds = total_seconds
	visible = total_seconds > 0.0 and remaining_seconds > 0.0
	_canvas.queue_redraw()


func stop() -> void:
	visible = false
	_total_seconds = 0.0


func _process(delta: float) -> void:
	if not visible:
		return

	# Held empty at zero rather than hidden: the server says when the item is gone.
	_remaining_seconds = maxf(_remaining_seconds - delta, 0.0)
	_canvas.queue_redraw()


func _draw_ring() -> void:
	if _total_seconds <= 0.0:
		return

	var centre := _canvas.size / 2.0
	var radius := minf(centre.x, centre.y) - _RING_WIDTH
	var left := clampf(_remaining_seconds / _total_seconds, 0.0, 1.0)
	_canvas.draw_arc(centre, radius, 0.0, TAU, 48, Color(0, 0, 0, 0.5), _RING_WIDTH + 2.0, true)
	if left > 0.0:
		_canvas.draw_arc(centre, radius, -PI / 2.0, -PI / 2.0 + TAU * left, 48, color, _RING_WIDTH, true)
