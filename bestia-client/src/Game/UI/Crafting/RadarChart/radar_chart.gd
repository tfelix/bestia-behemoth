extends Control
class_name CraftRadarChart
## One spoke per crafting axis. The mix being tried is filled in; a known blueprint picked to compare with is drawn
## as an outline on top, so the player sees which way to change the mix.
##
## A value of -1 sits at the centre and +1 at the rim, so 0 - "this axis does not matter" - is the middle ring. An
## unknown blueprint is never drawn: the chart only shows what the player already knows.

const _MIX_FILL := Color(0.35, 0.65, 1.0, 0.35)
const _MIX_LINE := Color(0.45, 0.75, 1.0)
const _OUTLINE := Color(1.0, 0.8, 0.3)
const _GRID := Color(1.0, 1.0, 1.0, 0.15)
const _LABEL := Color(1.0, 1.0, 1.0, 0.7)
const _LABEL_SIZE := 11
## Room around the chart for the pole names.
const _MARGIN := 34.0

var _mix := PackedFloat32Array()
var _outline := PackedFloat32Array()


## [param axes] holds one value per [constant CraftAxes.AXES] entry; an empty array clears the shape.
func set_mix(axes: PackedFloat32Array) -> void:
	_mix = axes
	queue_redraw()


func set_outline(axes: PackedFloat32Array) -> void:
	_outline = axes
	queue_redraw()


func _draw() -> void:
	var radius := minf(size.x, size.y) / 2.0 - _MARGIN
	if radius <= 0.0:
		return

	for ring in [0.5, 1.0]:
		_draw_closed(_ring(ring * radius), _GRID, 1.0)

	var font := get_theme_default_font()
	for i in CraftAxes.AXES.size():
		var tip := _point(i, 1.0, radius)
		draw_line(size / 2.0, tip, _GRID)
		var label := CraftAxes.pole_name(i, true)
		var label_size := font.get_string_size(label, HORIZONTAL_ALIGNMENT_LEFT, -1, _LABEL_SIZE)
		var outward := (tip - size / 2.0).normalized() * (label_size.x / 2.0 + 6.0)
		var at := tip + outward - Vector2(label_size.x / 2.0, -label_size.y / 4.0)
		# Kept inside the control, so a narrow window cuts no side label off.
		at.x = clampf(at.x, 0.0, size.x - label_size.x)
		draw_string(font, at, label, HORIZONTAL_ALIGNMENT_LEFT, -1, _LABEL_SIZE, _LABEL)

	if _mix.size() == CraftAxes.AXES.size():
		var shape := _shape(_mix, radius)
		# A mix squeezed into a sliver cannot be filled, only outlined.
		if not Geometry2D.triangulate_polygon(shape).is_empty():
			draw_colored_polygon(shape, _MIX_FILL)
		_draw_closed(shape, _MIX_LINE, 2.0)

	if _outline.size() == CraftAxes.AXES.size():
		_draw_closed(_shape(_outline, radius), _OUTLINE, 2.0)


func _shape(axes: PackedFloat32Array, radius: float) -> PackedVector2Array:
	var points := PackedVector2Array()
	for i in axes.size():
		points.append(_point(i, (clampf(axes[i], -1.0, 1.0) + 1.0) / 2.0, radius))
	return points


func _ring(at_radius: float) -> PackedVector2Array:
	var points := PackedVector2Array()
	for i in CraftAxes.AXES.size():
		points.append(_point(i, 1.0, at_radius))
	return points


## The first spoke points straight up; the rest follow clockwise.
func _point(axis: int, fraction: float, radius: float) -> Vector2:
	var angle := -PI / 2.0 + axis * TAU / CraftAxes.AXES.size()
	return size / 2.0 + Vector2(cos(angle), sin(angle)) * fraction * radius


func _draw_closed(points: PackedVector2Array, color: Color, width: float) -> void:
	if points.is_empty():
		return
	var closed := points.duplicate()
	closed.append(points[0])
	draw_polyline(closed, color, width, true)
