extends MarginContainer

signal row_selected(row)

## Emitted whenever this row's buffered (not-yet-confirmed) point investment changes, so the
## owning Skills list can recompute how many skill points are still available to spend across
## all rows and whether the footer's Confirm/Cancel buttons should show.
signal investment_changed(row)

const DISABLED_MODULATE: Color = Color(0.4, 0.4, 0.4, 0.6)
const SELECTED_MODULATE: Color = Color(0.75, 0.85, 1.0)
const PENDING_MODULATE: Color = Color(1.0, 0.9, 0.6)

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
var _selected: bool = false
var _max_skill_level: int = 1

# The level the server has confirmed as learned/invested (from the last SkillListSMSG) - this,
# not _max_skill_level, is the ceiling for what can be selected to actually Use or drag onto a
# shortcut, since a pending investment isn't real until the server has accepted it.
var _base_level: int = 0

# Points spent on this row via the "+" button since the last confirm/cancel - buffered locally,
# only sent to the server (InvestSkillPointCMSG) when the owning Skills list's Confirm button is
# pressed. Cleared on Cancel, and implicitly cleared for good on confirm since the resulting
# SkillListSMSG rebuild tears down and reinstantiates every row from scratch.
var _pending_points: int = 0

# Whether the entity this row belongs to is the account's own master - only a master invests
# skill points into the tree, so bestia rows (single-level, level-gated by species) must never
# offer the spend button even if the master still has unspent points lying around.
var _is_master_row: bool = false

# Whether the master (account-wide) still has skill points left to spend, broadcast in from
# Skills._update_skill_row_buttons - a row has no standing connection to that count otherwise.
var _can_spend_globally: bool = false

var _selected_skill_level: int = 1


func get_skill_name() -> String:
	return _skill_name.text


func get_selected_level() -> int:
	return _selected_skill_level


## Sets up the row from a single SkillListEntry - looks up the matching AttackDB entry
## itself (falling back to a placeholder "Unknown Skill" display if it's missing).
func initialize(entry: SkillListEntry) -> void:
	var attack: AttackResource = AttackDB.get_instance().get_attack(entry.SkillId)

	skill_id = entry.SkillId
	_base_level = entry.Level
	_pending_points = 0

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

	_selected_skill_level = clampi(_base_level, 1, maxi(_base_level, 1))

	# Regular bestia skills are always single-level (max_level == 1) - only a bestia
	# master's skill tree has multiple ranks worth choosing between, so the selector
	# only needs to be data-driven, not aware of which kind of entity this row belongs to.
	var show_level_selector = _max_skill_level > 1
	_level_minus_button.visible = show_level_selector
	_level_plus_button.visible = show_level_selector

	_refresh_display()


## Called by Skills (the owning list) so a row knows whether it belongs to the account's own
## master (the only entity that can spend skill points) or one of its bestias.
func set_is_master_row(is_master_row: bool) -> void:
	_is_master_row = is_master_row
	_refresh_display()


func _update_skill_level_label() -> void:
	if _pending_points > 0:
		_skill_level.text = "Lv: %s / %s (+%s pending)" % [_selected_skill_level, _max_skill_level, _pending_points]
	else:
		_skill_level.text = "Lv: %s / %s" % [_selected_skill_level, _max_skill_level]


func _on_level_minus_button_pressed() -> void:
	_selected_skill_level = clampi(_selected_skill_level - 1, 1, maxi(_base_level, 1))
	_update_skill_level_label()


func _on_level_plus_button_pressed() -> void:
	# Capped at _base_level (server-confirmed), not _max_skill_level (the tree's overall cap) -
	# a pending, unconfirmed investment isn't usable yet.
	_selected_skill_level = clampi(_selected_skill_level + 1, 1, maxi(_base_level, 1))
	_update_skill_level_label()


## Recomputes every part of the row's display from _base_level/_pending_points/_max_skill_level -
## called after initialize(), a pending investment change, or a Cancel reset.
func _refresh_display() -> void:
	var effective_level = _base_level + _pending_points
	var learned = effective_level > 0

	_selected_skill_level = clampi(_selected_skill_level, 1, maxi(_base_level, 1))
	_update_skill_level_label()

	if learned:
		_detail_row.show()
		_not_available_label.hide()
	else:
		_detail_row.hide()
		_not_available_label.show()

	var can_invest_more = _is_master_row and effective_level < _max_skill_level
	_spend_skill_point_button.visible = _can_spend_globally and can_invest_more

	_update_modulate()


## Called by Skills (the owning list) whenever the account's spendable skill point count
## changes, since a row has no standing connection to that state otherwise.
func set_can_spend_points(can_spend: bool) -> void:
	_can_spend_globally = can_spend
	_refresh_display()


## Called by Skills (the owning list) to highlight whichever row was last clicked, so the
## footer's Use button knows what to activate.
func set_selected(selected: bool) -> void:
	_selected = selected
	_update_modulate()


func has_pending_investment() -> bool:
	return _pending_points > 0


## Returns the {attack_id, amount} this row wants to invest, for Skills to batch into a single
## InvestSkillPointCMSG on Confirm. Empty if nothing is buffered.
func get_pending_investment() -> Dictionary:
	if _pending_points <= 0:
		return {}
	return {"attack_id": skill_id, "amount": _pending_points}


## Called by Skills (the owning list) when Cancel is pressed, discarding any buffered,
## not-yet-confirmed investment on this row.
func reset_pending_investment() -> void:
	if _pending_points == 0:
		return
	_pending_points = 0
	_refresh_display()


func _update_modulate() -> void:
	if _base_level + _pending_points == 0:
		self_modulate = DISABLED_MODULATE
	elif _pending_points > 0:
		self_modulate = PENDING_MODULATE
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
	if _base_level == 0:
		return
	if event is InputEventMouseButton and event.pressed and event.button_index == MOUSE_BUTTON_LEFT:
		row_selected.emit(self)
		accept_event()


## Allows a learned skill to be dragged onto a hotbar shortcut slot at the currently
## selected level (see ShortcutContainer._can_drop_data, which already accepts "skill").
func _get_drag_data(_at_position: Vector2) -> Variant:
	if _base_level == 0:
		return null

	var preview: TextureRect = TextureRect.new()
	preview.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	preview.size = Vector2(50, 50)
	preview.pivot_offset = preview.size / 2.0
	preview.texture = _icon.texture
	set_drag_preview(preview)

	return {"type": "skill", "id": skill_id, "level": _selected_skill_level}


## Buffers a level-up locally - nothing is sent to the server until Skills' Confirm button is
## pressed and batches every row's pending investment into one InvestSkillPointCMSG.
func _on_spend_skill_point_button_pressed() -> void:
	var effective_level = _base_level + _pending_points
	if not _is_master_row or not _can_spend_globally or effective_level >= _max_skill_level:
		return

	_pending_points += 1
	_refresh_display()
	investment_changed.emit(self)
