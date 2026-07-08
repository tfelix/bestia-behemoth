extends Window
class_name WidgetWindow


@export var content: PackedScene


func _ready() -> void:
	var content_scn = content.instantiate()
	add_child(content_scn)


func _process(_delta: float) -> void:
	if not visible:
		return
	var bounds := get_tree().root.size
	var clamped_position := position
	clamped_position.x = clampi(clamped_position.x, 0, maxi(0, bounds.x - size.x))
	clamped_position.y = clampi(clamped_position.y, 0, maxi(0, bounds.y - size.y))
	if clamped_position != position:
		position = clamped_position
