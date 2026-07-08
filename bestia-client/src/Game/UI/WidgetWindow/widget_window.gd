extends Window
class_name WidgetWindow


@export var content: PackedScene

var _content_control: Control


func _ready() -> void:
	close_requested.connect(hide)

	var content_scn = content.instantiate()
	add_child(content_scn)
	if content_scn is Control:
		_content_control = content_scn
		# Mirror the content's own rect, not its minimum size: containers
		# like ScrollContainer deliberately don't report their scrollable
		# content's minimum size, so relying on minimum size collapses
		# the window instead of matching the content's authored size.
		_content_control.resized.connect(_update_size_to_content)
	_update_size_to_content()


func _update_size_to_content() -> void:
	if _content_control == null:
		return
	size = Vector2i(_content_control.size.ceil())


func get_content() -> Control:
	return _content_control


func _process(_delta: float) -> void:
	if not visible:
		return
	var bounds := get_tree().root.size
	var clamped_position := position
	clamped_position.x = clampi(clamped_position.x, 0, maxi(0, bounds.x - size.x))
	clamped_position.y = clampi(clamped_position.y, 0, maxi(0, bounds.y - size.y))
	if clamped_position != position:
		position = clamped_position
