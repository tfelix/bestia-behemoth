extends MarginContainer

signal row_selected(row)

const DISABLED_MODULATE: Color = Color(0.4, 0.4, 0.4, 0.6)
const SELECTED_MODULATE: Color = Color(0.75, 0.85, 1.0)

@onready var _spend_skill_point_button = %SpendSkillPointButton
@onready var _icon: TextureRect = %TextureRect
@onready var _skill_name: Label = %SkillName
@onready var _skill_level: Label = %SkillLevel
@onready var _mana_label: Label = %ManaLabel
@onready var _level_minus_button: Button = %LevelMinusButton
@onready var _level_plus_button: Button = %LevelPlusButton

var attack_id: int = -1
var _disabled: bool = false
var _selected: bool = false
var _max_skill_level: int = 1
var _selected_skill_level: int = 1


func get_skill_name() -> String:
	return _skill_name.text


func get_selected_level() -> int:
	return _selected_skill_level


func set_data(p_attack_id: int, p_name: String, p_icon: Texture2D, p_level: int, p_max_level: int, p_mana_cost: int) -> void:
	attack_id = p_attack_id
	_skill_name.text = p_name
	_icon.texture = p_icon
	_mana_label.text = "Mana: %s" % [p_mana_cost]

	_max_skill_level = p_max_level
	_selected_skill_level = clampi(p_level, 1, _max_skill_level)
	_update_skill_level_label()

	# Regular bestia skills are always single-level (max_level == 1) - only a bestia
	# master's skill tree has multiple ranks worth choosing between, so the selector
	# only needs to be data-driven, not aware of which kind of entity this row belongs to.
	var show_level_selector = _max_skill_level > 1
	_level_minus_button.visible = show_level_selector
	_level_plus_button.visible = show_level_selector


func _update_skill_level_label() -> void:
	_skill_level.text = "Lv: %s / %s" % [_selected_skill_level, _max_skill_level]


func _on_level_minus_button_pressed() -> void:
	_selected_skill_level = clampi(_selected_skill_level - 1, 1, _max_skill_level)
	_update_skill_level_label()


func _on_level_plus_button_pressed() -> void:
	_selected_skill_level = clampi(_selected_skill_level + 1, 1, _max_skill_level)
	_update_skill_level_label()


## Dims the row and blocks its interactive buttons for a skill that isn't learned/active yet,
## while still showing it so the player can see what's available.
func set_disabled(disabled: bool) -> void:
	_disabled = disabled
	_spend_skill_point_button.disabled = disabled
	_level_minus_button.disabled = disabled
	_level_plus_button.disabled = disabled
	_update_modulate()


## Called by Skills (the owning list) whenever the account's spendable skill point count
## changes, since a row has no standing connection to that state otherwise.
func set_can_spend_points(can_spend: bool) -> void:
	_spend_skill_point_button.visible = can_spend


## Called by Skills (the owning list) to highlight whichever row was last clicked, so the
## footer's Use button knows what to activate.
func set_selected(selected: bool) -> void:
	_selected = selected
	_update_modulate()


func _update_modulate() -> void:
	if _disabled:
		self_modulate = DISABLED_MODULATE
	elif _selected:
		self_modulate = SELECTED_MODULATE
	else:
		self_modulate = Color.WHITE


func _gui_input(event: InputEvent) -> void:
	if _disabled:
		return
	if event is InputEventMouseButton and event.pressed and event.button_index == MOUSE_BUTTON_LEFT:
		row_selected.emit(self)
		accept_event()


## Allows a learned skill to be dragged onto a hotbar shortcut slot at the currently
## selected level (see ShortcutContainer._can_drop_data, which already accepts "attack").
func _get_drag_data(_at_position: Vector2) -> Variant:
	if _disabled:
		return null

	var preview: TextureRect = TextureRect.new()
	preview.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	preview.size = Vector2(50, 50)
	preview.pivot_offset = preview.size / 2.0
	preview.texture = _icon.texture
	set_drag_preview(preview)

	return {"type": "attack", "id": attack_id, "level": _selected_skill_level}
