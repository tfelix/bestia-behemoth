extends PanelContainer
class_name Skills

const SkillRowScene = preload("res://Game/UI/Skills/SkillRow/SkillRow.tscn")

@onready var _search_line_edit = %SearchLineEdit
@onready var _skill_rows = %SkillRows
@onready var _skill_points_label: Label = %SkillPointsLabel

# The account's own master entity id, used to tell whether the entity a SkillListSMSG describes
# is the master (show Skill Points) or one of its bestias (hide it) - bestias never learn skills
# via spendable points, only via level-up or item-taught custom skills.
var _master_entity_id: int = 0
var _current_entity_id: int = 0
var _available_skill_points: int = 0
var _selected_row: Control = null


func _ready() -> void:
	_skill_points_label.visible = false
	ConnectionManager.connect("self_received", _on_self_received)
	ConnectionManager.connect("entity_received", _on_entity_received)


## Called by Game/UI/ui.gd whenever the Skills window is opened, since the window content is
## a Control nested inside a Window - a Window's own visibility does not propagate as a
## visibility_changed signal to its content the way regular Control nesting would.
func request_refresh() -> void:
	if ConnectionManager.is_ready_to_send():
		ConnectionManager.get_skills()


func _on_self_received(msg: SelfSMSG) -> void:
	_master_entity_id = msg.MasterEntityId


func _on_entity_received(msg: EntitySMSG) -> void:
	if msg is SkillListSMSG:
		_current_entity_id = msg.EntityId
		_skill_points_label.visible = msg.EntityId == _master_entity_id
		_populate_rows(msg)
	elif msg is SkillPointsComponentSMSG:
		if msg.EntityId == _current_entity_id:
			_skill_points_label.text = "Skill Points: %s" % [msg.Points]
			_available_skill_points = msg.Points
			_update_skill_row_buttons()


func _populate_rows(msg: SkillListSMSG) -> void:
	for child in _skill_rows.get_children():
		child.queue_free()
	_selected_row = null

	for entry in msg.Skills:
		var attack: AttackResource = AttackDB.get_instance().get_attack(entry.AttackId)
		var row = SkillRowScene.instantiate()
		_skill_rows.add_child(row)

		if attack:
			row.set_data(entry.AttackId, attack.name, attack.icon, entry.Level, entry.MaxLevel, attack.mana_cost)
		else:
			printerr("Skills: Attack ID %s not found in AttackDB, can not display it" % [entry.AttackId])
			row.set_data(entry.AttackId, "Unknown Skill", null, entry.Level, entry.MaxLevel, 0)

		row.set_disabled(!entry.Learned)
		row.set_can_spend_points(_available_skill_points > 0)
		row.row_selected.connect(_on_row_selected)

	_perform_skill_search()


## Highlights whichever row was last clicked so the footer's Use button knows what to
## activate - rows have no standing selection state of their own beyond this.
func _on_row_selected(row: Control) -> void:
	if _selected_row and is_instance_valid(_selected_row):
		_selected_row.set_selected(false)
	_selected_row = row
	_selected_row.set_selected(true)


func _on_use_button_pressed() -> void:
	if _selected_row == null or not is_instance_valid(_selected_row):
		return
	ConnectionManager.activate_skill(_selected_row.attack_id, _selected_row.get_selected_level())


## Broadcasts the current spendable skill point count to every row so a SpendSkillPointButton
## can hide itself once there are no points left to spend - rows have no standing connection
## to this state on their own since they're plain instantiated children.
func _update_skill_row_buttons() -> void:
	for row in _skill_rows.get_children():
		row.set_can_spend_points(_available_skill_points > 0)


func _on_clear_button_pressed() -> void:
	_search_line_edit.text = ""
	_perform_skill_search()


## Skills is instantiated as a WidgetWindow's content (see ui.gd), so hiding
## this panel alone would leave the surrounding window visible - hide the
## parent WidgetWindow instead, mirroring its own title bar close button.
func _on_close_button_pressed() -> void:
	get_parent().hide()


func _on_search_line_edit_text_changed(_new_text: String) -> void:
	_perform_skill_search()


func _perform_skill_search() -> void:
	var query = _search_line_edit.text.strip_edges().to_lower()
	for row in _skill_rows.get_children():
		row.visible = query.is_empty() or row.get_skill_name().to_lower().contains(query)
