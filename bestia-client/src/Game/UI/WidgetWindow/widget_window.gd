extends VBoxContainer
class_name WidgetWindow


@export var content: PackedScene
@export var window_title: String = "":
	set(value):
		window_title = value
		if is_node_ready():
			_title_label.text = value

@onready var _title_bar: PanelContainer = %TitleBar
@onready var _title_label: Label = %TitleLabel
@onready var _close_button: Button = %CloseButton

var _content_control: Control
var _dragging: bool = false


func _ready() -> void:
	_title_label.text = window_title
	_title_bar.mouse_default_cursor_shape = Control.CURSOR_MOVE
	_title_bar.gui_input.connect(_on_title_bar_gui_input)
	_close_button.pressed.connect(hide)

	var content_scn = content.instantiate()
	add_child(content_scn)
	if content_scn is Control:
		_content_control = content_scn
		# Mirror the content's own rect, not its minimum size: containers
		# like ScrollContainer deliberately don't report their scrollable
		# content's minimum size, so relying on minimum size collapses
		# the widget instead of matching the content's authored size.
		_content_control.resized.connect(_update_size_to_content)
	_update_size_to_content()
	_center_in_viewport()


func _update_size_to_content() -> void:
	if _content_control == null:
		return
	_content_control.custom_minimum_size = _content_control.size.ceil()
	size = get_combined_minimum_size()


func _center_in_viewport() -> void:
	var bounds := get_viewport_rect().size
	position = ((bounds - size) / 2).round()


func get_content() -> Control:
	return _content_control


func _on_title_bar_gui_input(event: InputEvent) -> void:
	if event is InputEventMouseButton and event.button_index == MOUSE_BUTTON_LEFT:
		_dragging = event.pressed
	elif event is InputEventMouseMotion and _dragging:
		position += event.relative


func _process(_delta: float) -> void:
	if not visible:
		return
	var bounds := get_viewport_rect().size
	var clamped_position := position
	clamped_position.x = clampf(clamped_position.x, 0, maxf(0, bounds.x - size.x))
	clamped_position.y = clampf(clamped_position.y, 0, maxf(0, bounds.y - size.y))
	if clamped_position != position:
		position = clamped_position
