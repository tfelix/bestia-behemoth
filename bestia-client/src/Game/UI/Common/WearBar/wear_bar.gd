extends ProgressBar
class_name WearBar

## The thin bar under an item icon that shows how worn the copy is. Hidden for an item that does not wear.

## Colours for the wear bar, worst state last. Fill is picked by [method _wear_color].
const _WEAR_FINE := Color(0.36, 0.71, 0.35)
const _WEAR_WORN := Color(0.85, 0.72, 0.22)
const _WEAR_BROKEN := Color(0.79, 0.27, 0.24)


func show_wear(durability: int, max_durability: int) -> void:
	visible = max_durability > 0
	if not visible:
		return

	max_value = max_durability
	value = durability

	var fill := StyleBoxFlat.new()
	fill.bg_color = _wear_color(durability, max_durability)
	fill.set_corner_radius_all(2)
	add_theme_stylebox_override("fill", fill)


static func _wear_color(durability: int, max_durability: int) -> Color:
	var fraction := float(durability) / float(max_durability)
	if durability <= 0:
		return _WEAR_BROKEN
	if fraction < 0.35:
		return _WEAR_WORN
	return _WEAR_FINE
