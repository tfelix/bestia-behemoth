extends Control
## Master (character) creation screen.
##
## Lets the player pick gender, body, face, hair and the hair/skin color, enter a
## name and create the character. The request is sent to the server and the result
## (success or error) is reported back via the ConnectionManager operation signals.

const MASTER_SELECT_SCENE := "res://Menu/MasterSelect/MasterSelect.tscn"

# Mirrors the proto OpSuccess / OpError enum values (see operation_success.proto / operation_error.proto).
const OP_SUCCESS_MASTER_CREATED := 0
const OP_ERROR_NAME_ALREADY_TAKEN := 0
const OP_ERROR_MAX_MASTERS_REACHED := 1
const OP_ERROR_INVALID_NAME := 2
const OP_ERROR_GENERAL := 3

# Gender -> list of selectable body types. The body value is the proto BodyType enum int.
# Only BODY_M_1 (0) exists so far, so every gender currently maps to that single body.
# Adding real per-gender bodies later is just extending these lists (and the proto enum).
const GENDER_BODIES := {
	0: [{"label": "Type 1", "body": 0}], # Male
	1: [{"label": "Type 1", "body": 0}], # Female
	2: [{"label": "Type 1", "body": 0}], # Diverse
}

@onready var _gender_button: OptionButton = $"CenterContainer/P/M/Main/Creation/Left/Entry1/Row/GenderButton"
@onready var _gender_left: Button = $"CenterContainer/P/M/Main/Creation/Left/Entry1/Row/L"
@onready var _gender_right: Button = $"CenterContainer/P/M/Main/Creation/Left/Entry1/Row/R"

@onready var _body_button: OptionButton = $"CenterContainer/P/M/Main/Creation/Left/Entry2/Row2/Body"
@onready var _body_left: Button = $"CenterContainer/P/M/Main/Creation/Left/Entry2/Row2/L"
@onready var _body_right: Button = $"CenterContainer/P/M/Main/Creation/Left/Entry2/Row2/R"

@onready var _face_button: OptionButton = $"CenterContainer/P/M/Main/Creation/Left/Entry3/Row2/Body"
@onready var _face_left: Button = $"CenterContainer/P/M/Main/Creation/Left/Entry3/Row2/L"
@onready var _face_right: Button = $"CenterContainer/P/M/Main/Creation/Left/Entry3/Row2/R"

@onready var _hair_button: OptionButton = $"CenterContainer/P/M/Main/Creation/Left/Entry4/Row2/Options"
@onready var _hair_left: Button = $"CenterContainer/P/M/Main/Creation/Left/Entry4/Row2/L"
@onready var _hair_right: Button = $"CenterContainer/P/M/Main/Creation/Left/Entry4/Row2/R"

@onready var _name_edit: LineEdit = $"CenterContainer/P/M/Main/Creation/Middle/Name/NameEdit"
@onready var _hair_color_grid: GridContainer = $"CenterContainer/P/M/Main/Creation/Right/Entry1/GridContainer"
@onready var _skin_color_grid: GridContainer = $"CenterContainer/P/M/Main/Creation/Right/Entry2/GridContainer"
@onready var _create_button: Button = $"CenterContainer/P/M/Main/Bottom/CreateButton"
@onready var _main: VBoxContainer = $"CenterContainer/P/M/Main"

var _selected_hair_swatch: ColorRect = null
var _selected_skin_swatch: ColorRect = null
var _hair_border: Panel = null
var _skin_border: Panel = null
var _status_label: Label = null


func _ready() -> void:
	_status_label = _make_status_label()

	_setup_color_grid(_hair_color_grid, true)
	_setup_color_grid(_skin_color_grid, false)

	_populate_body_for_gender()

	_gender_button.item_selected.connect(func(_i): _populate_body_for_gender())
	_connect_arrows(_gender_left, _gender_right, _gender_button, true)
	_connect_arrows(_body_left, _body_right, _body_button, false)
	_connect_arrows(_face_left, _face_right, _face_button, false)
	_connect_arrows(_hair_left, _hair_right, _hair_button, false)

	ConnectionManager.operation_success.connect(_on_operation_success)
	ConnectionManager.operation_error.connect(_on_operation_error)


func _exit_tree() -> void:
	if ConnectionManager.operation_success.is_connected(_on_operation_success):
		ConnectionManager.operation_success.disconnect(_on_operation_success)
	if ConnectionManager.operation_error.is_connected(_on_operation_error):
		ConnectionManager.operation_error.disconnect(_on_operation_error)


## Rebuilds the body option list based on the currently selected gender.
func _populate_body_for_gender() -> void:
	var gender_id := _gender_button.get_selected_id()
	var bodies: Array = GENDER_BODIES.get(gender_id, GENDER_BODIES[0])
	_body_button.clear()
	for i in bodies.size():
		_body_button.add_item(bodies[i]["label"], i)
		_body_button.set_item_metadata(i, bodies[i]["body"])
	if bodies.size() > 0:
		_body_button.select(0)


func _connect_arrows(left: Button, right: Button, option: OptionButton, is_gender: bool) -> void:
	left.pressed.connect(_step_option.bind(option, -1, is_gender))
	right.pressed.connect(_step_option.bind(option, 1, is_gender))


## Steps an OptionButton selection with wrap-around and mirrors the item_selected side effects.
func _step_option(option: OptionButton, direction: int, is_gender: bool) -> void:
	var count := option.item_count
	if count <= 1:
		return
	var next := (option.selected + direction + count) % count
	option.select(next)
	if is_gender:
		_populate_body_for_gender()


func _setup_color_grid(grid: GridContainer, is_hair: bool) -> void:
	var border := _make_border()
	if is_hair:
		_hair_border = border
	else:
		_skin_border = border

	var first_swatch: ColorRect = null
	for child in grid.get_children():
		if child is ColorRect:
			var swatch: ColorRect = child
			# Guarantee the swatch reacts to clicks regardless of the scene default.
			swatch.mouse_filter = Control.MOUSE_FILTER_STOP
			swatch.gui_input.connect(_on_swatch_input.bind(swatch, is_hair))
			if first_swatch == null:
				first_swatch = swatch

	if first_swatch != null:
		_select_swatch(first_swatch, is_hair)


func _on_swatch_input(event: InputEvent, swatch: ColorRect, is_hair: bool) -> void:
	if event is InputEventMouseButton and event.button_index == MOUSE_BUTTON_LEFT and event.pressed:
		_select_swatch(swatch, is_hair)


func _select_swatch(swatch: ColorRect, is_hair: bool) -> void:
	var border := _hair_border if is_hair else _skin_border
	if border.get_parent() != null:
		border.get_parent().remove_child(border)
	swatch.add_child(border)

	if is_hair:
		_selected_hair_swatch = swatch
	else:
		_selected_skin_swatch = swatch


func _on_create_button_pressed() -> void:
	var character_name := _name_edit.text.strip_edges()
	if character_name.is_empty():
		_show_status("Please enter a character name.", true)
		return

	var body := int(_body_button.get_selected_metadata()) if _body_button.get_selected_metadata() != null else 0
	var face := _face_button.get_selected_id()
	var hair := _hair_button.get_selected_id()
	var hair_color := _selected_hair_swatch.color if _selected_hair_swatch != null else Color.BLACK
	var skin_color := _selected_skin_swatch.color if _selected_skin_swatch != null else Color.BLACK

	_create_button.disabled = true
	_show_status("Creating character...", false)
	ConnectionManager.create_master(character_name, body, face, hair, hair_color, skin_color)


func _on_cancel_button_pressed() -> void:
	_goto_master_selection()


func _on_operation_success(message) -> void:
	if message.Code == OP_SUCCESS_MASTER_CREATED:
		_goto_master_selection()


func _on_operation_error(message) -> void:
	_create_button.disabled = false
	match message.Code:
		OP_ERROR_NAME_ALREADY_TAKEN:
			_show_status("That name is already taken.", true)
		OP_ERROR_MAX_MASTERS_REACHED:
			_show_status("You have reached the maximum number of characters.", true)
		OP_ERROR_INVALID_NAME:
			_show_status("That name is not valid.", true)
		_:
			_show_status("Could not create the character. Please try again.", true)


func _goto_master_selection() -> void:
	SceneManager.goto_scene(MASTER_SELECT_SCENE)


func _show_status(text: String, is_error: bool) -> void:
	_status_label.text = text
	_status_label.modulate = Color(1, 0.5, 0.5) if is_error else Color(1, 1, 1)


func _make_status_label() -> Label:
	var label := Label.new()
	label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	_main.add_child(label)
	return label


## Creates a reusable selection outline shown on the currently selected color swatch.
func _make_border() -> Panel:
	var panel := Panel.new()
	panel.mouse_filter = Control.MOUSE_FILTER_IGNORE
	panel.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	var style := StyleBoxFlat.new()
	style.bg_color = Color(0, 0, 0, 0)
	style.set_border_width_all(3)
	style.border_color = Color(1, 1, 1)
	panel.add_theme_stylebox_override("panel", style)
	return panel
