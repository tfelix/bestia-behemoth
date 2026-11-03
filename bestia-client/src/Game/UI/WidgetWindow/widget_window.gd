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
## Until the player drags the window somewhere, it stays centred on whatever size it settles at.
var _placed_by_player: bool = false


func _ready() -> void:
	# Once here rather than in each of the six windows built from this scene: what they have in common is
	# that the player is reading them, so the world behind them is not theirs to point at.
	add_to_group("world_blocking_ui")

	_title_label.text = window_title
	_title_bar.mouse_default_cursor_shape = Control.CURSOR_MOVE
	_title_bar.gui_input.connect(_on_title_bar_gui_input)
	_close_button.pressed.connect(hide)

	var content_scn = content.instantiate()
	var authored_size := Vector2.ZERO
	if content_scn is Control:
		authored_size = content_scn.size
	add_child(content_scn)
	if content_scn is Control:
		_content_control = content_scn
		# The content's authored rect is the floor, not just its minimum size: containers like ScrollContainer
		# deliberately don't report their scrollable content's minimum size, so relying on minimum size alone
		# collapses the widget instead of matching the content's authored size.
		_content_control.custom_minimum_size = authored_size.ceil()
	minimum_size_changed.connect(_fit_to_content)
	_fit_to_content()


## Follows the minimum size both ways. Keeping the largest rect the content ever had kept the one from its first
## layout, where an autowrapped Label is measured at zero width and asks for a line per glyph: the party window
## came up a screen tall.
func _fit_to_content() -> void:
	size = get_combined_minimum_size()
	if not _placed_by_player:
		_center_in_viewport()


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
		_placed_by_player = true


func _process(_delta: float) -> void:
	if not visible:
		return
	var bounds := get_viewport_rect().size
	var clamped_position := position
	clamped_position.x = clampf(clamped_position.x, 0, maxf(0, bounds.x - size.x))
	clamped_position.y = clampf(clamped_position.y, 0, maxf(0, bounds.y - size.y))
	if clamped_position != position:
		position = clamped_position
