class_name MapContextCard
extends PanelContainer

## What a click on the map opens: what is there, where it is, and what can be done about it - go, add it to a
## route, mark it. The overlay decides the content; this only lays it out beside the click and keeps it on screen.

const _TITLE_SIZE := 15
const _LINE_SIZE := 12
const _GAP := 12.0

var _rows: VBoxContainer


func _ready() -> void:
	visible = false
	mouse_filter = Control.MOUSE_FILTER_STOP
	var style := StyleBoxFlat.new()
	style.bg_color = Color(0.11, 0.095, 0.08, 0.96)
	style.border_color = Color(0.62, 0.50, 0.32)
	style.set_border_width_all(1)
	style.set_content_margin_all(8)
	add_theme_stylebox_override("panel", style)

	_rows = VBoxContainer.new()
	_rows.add_theme_constant_override("separation", 4)
	add_child(_rows)


## Shows [param title] and [param lines] beside [param at], in the parent's coordinates, with one button per
## [code]{label, run}[/code] in [param actions] - each closes the card and then runs.
func present(at: Vector2, title: String, lines: Array[String], actions: Array[Dictionary]) -> void:
	# Taken out at once rather than only freed: a row freed at the end of the frame would still be measured below.
	for child in _rows.get_children():
		_rows.remove_child(child)
		child.queue_free()

	_rows.add_child(_label(title, _TITLE_SIZE, Color(1.0, 0.86, 0.35)))
	for line in lines:
		if not line.is_empty():
			_rows.add_child(_label(line, _LINE_SIZE, Color(0.97, 0.93, 0.85)))

	for action in actions:
		var button := Button.new()
		button.text = action["label"]
		button.focus_mode = Control.FOCUS_NONE
		button.alignment = HORIZONTAL_ALIGNMENT_LEFT
		button.add_theme_font_size_override("font_size", _LINE_SIZE + 1)
		var run: Callable = action["run"]
		button.pressed.connect(func() -> void:
			close()
			run.call()
		)
		_rows.add_child(button)

	visible = true
	reset_size()
	await get_tree().process_frame
	reset_size()
	_place(at)


func close() -> void:
	visible = false


func _place(at: Vector2) -> void:
	var bounds := get_parent_area_size()
	var spot := at + Vector2(_GAP, _GAP)
	spot.x = minf(spot.x, bounds.x - size.x)
	spot.y = minf(spot.y, bounds.y - size.y)
	position = spot.max(Vector2.ZERO)


func _label(text: String, points: int, ink: Color) -> Label:
	var label := Label.new()
	label.text = text
	label.add_theme_font_size_override("font_size", points)
	label.add_theme_color_override("font_color", ink)
	return label
