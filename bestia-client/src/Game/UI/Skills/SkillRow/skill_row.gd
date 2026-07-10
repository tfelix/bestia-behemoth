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
@onready var _not_available_label: Label = %NotAvailable
@onready var _detail_row: HBoxContainer = %DetailRow

var skill_id: int = -1
var _disabled: bool = false
var _selected: bool = false
var _max_skill_level: int = 1
var _selected_skill_level: int = 1


func get_skill_name() -> String:
	return _skill_name.text


func get_selected_level() -> int:
	return _selected_skill_level


## Sets up the row from a single SkillListEntry - looks up the matching AttackDB entry
## itself (falling back to a placeholder "Unknown Skill" display if it's missing) and
## disables the row when the entry isn't learned yet (Level == 0).
func initialize(entry: SkillListEntry) -> void:
	var attack: AttackResource = AttackDB.get_instance().get_attack(entry.SkillId)

	skill_id = entry.SkillId

	if attack:
		_skill_name.text = attack.name
		_icon.texture = attack.icon
		_mana_label.text = "Mana: %s" % [attack.mana_cost]
		# Empty tooltip_text disables the hover tooltip entirely (a skill with no
		# description_key yet).
		tooltip_text = tr(attack.description_key) if not attack.description_key.is_empty() else ""
		_max_skill_level = attack.max_level
	else:
		printerr("Skills: Skill ID %s not found in AttackDB, can not display it" % [entry.SkillId])
		_skill_name.text = "Unknown Skill"
		_icon.texture = null
		_mana_label.text = "Mana: %s" % [0]
		tooltip_text = ""
		_max_skill_level = 1

	_selected_skill_level = clampi(entry.Level, 1, _max_skill_level)
	_update_skill_level_label()

	# Regular bestia skills are always single-level (max_level == 1) - only a bestia
	# master's skill tree has multiple ranks worth choosing between, so the selector
	# only needs to be data-driven, not aware of which kind of entity this row belongs to.
	var show_level_selector = _max_skill_level > 1
	_level_minus_button.visible = show_level_selector
	_level_plus_button.visible = show_level_selector

	_set_disabled(entry.Level == 0)


func _update_skill_level_label() -> void:
	_skill_level.text = "Lv: %s / %s" % [_selected_skill_level, _max_skill_level]


func _on_level_minus_button_pressed() -> void:
	_selected_skill_level = clampi(_selected_skill_level - 1, 1, _max_skill_level)
	_update_skill_level_label()


func _on_level_plus_button_pressed() -> void:
	_selected_skill_level = clampi(_selected_skill_level + 1, 1, _max_skill_level)
	_update_skill_level_label()


func _set_disabled(disabled: bool) -> void:
	_disabled = disabled
	_spend_skill_point_button.disabled = disabled
	_level_minus_button.disabled = disabled
	_level_plus_button.disabled = disabled
	
	if disabled:
		_detail_row.hide()
		_not_available_label.show()
	else:
		_detail_row.show()
		_not_available_label.hide()
	
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


## Skill descriptions are authored as BBCode (see skills.csv), so the default
## Label-based tooltip would render markup literally - swap in a RichTextLabel instead.
## Called by the engine once the mouse has hovered motionless over this row for
## ProjectSettings "gui/timers/tooltip_delay_sec" (see project.godot).
func _make_custom_tooltip(for_text: String) -> Object:
	if for_text.is_empty():
		return null

	var label := RichTextLabel.new()
	label.bbcode_enabled = true
	label.fit_content = true
	label.scroll_active = false
	label.custom_minimum_size = Vector2(320, 0)
	label.text = for_text

	var panel := PanelContainer.new()
	panel.add_child(label)
	return panel


func _gui_input(event: InputEvent) -> void:
	if _disabled:
		return
	if event is InputEventMouseButton and event.pressed and event.button_index == MOUSE_BUTTON_LEFT:
		row_selected.emit(self)
		accept_event()


## Allows a learned skill to be dragged onto a hotbar shortcut slot at the currently
## selected level (see ShortcutContainer._can_drop_data, which already accepts "skill").
func _get_drag_data(_at_position: Vector2) -> Variant:
	if _disabled:
		return null

	var preview: TextureRect = TextureRect.new()
	preview.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	preview.size = Vector2(50, 50)
	preview.pivot_offset = preview.size / 2.0
	preview.texture = _icon.texture
	set_drag_preview(preview)

	return {"type": "skill", "id": skill_id, "level": _selected_skill_level}


func _on_spend_skill_point_button_pressed() -> void:
	# TODO
	# save the level up intend temporary in the node here
	# tell the parent skill to decrement available skill points (how? maybe emit signal?)
	# when apply is clicked new skill are send to the server, server validates it
	# server send new skill list with updated level
	# client updates available skill points 
	# when cancel is clicked on the parent available skill points are resettet and all childs are told
	# to reset back their temp. skill points.
	# important: must mimic the dep graph of skills. If a pre-req is leveld up with some initial avail skill points
	# then the newly added skills must be set as active.
	print("geht")
