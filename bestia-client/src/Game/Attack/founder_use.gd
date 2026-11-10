class_name FounderUse
extends AttackUse

## Founder takes the town's name before the cast: the server keeps it for the cast that follows, and the sign
## is planted where the master stands, so there is nothing to aim.

## The cast waiting for the server to accept its name. A refused name leaves it waiting until the next try.
var _attack: AttackResource = null
var _level: int = 0


func on_skill_activated(attack: AttackResource, level: int) -> void:
	if SkillCooldowns.remaining_seconds(attack.skill_id) > 0.0:
		return
	if not ConnectionManager.settlement_planned.is_connected(_on_planned):
		ConnectionManager.settlement_planned.connect(_on_planned)

	var dialog := ConfirmationDialog.new()
	dialog.title = TranslationServer.translate("SETTLEMENT_NAME_TITLE")
	var name_edit := LineEdit.new()
	name_edit.max_length = 24
	name_edit.placeholder_text = TranslationServer.translate("SETTLEMENT_NAME_PLACEHOLDER")
	dialog.add_child(name_edit)
	dialog.register_text_enter(name_edit)

	var tree := Engine.get_main_loop() as SceneTree
	tree.root.add_child(dialog)
	dialog.confirmed.connect(func() -> void: _plan(attack, level, name_edit.text.strip_edges()))
	dialog.visibility_changed.connect(func() -> void: if not dialog.visible: dialog.queue_free())
	dialog.popup_centered(Vector2i(320, 90))
	name_edit.grab_focus()


func _plan(attack: AttackResource, level: int, town_name: String) -> void:
	if town_name.is_empty():
		return
	_attack = attack
	_level = level
	ConnectionManager.plan_settlement(town_name)


func _on_planned(_msg: SettlementPlannedSMSG) -> void:
	if _attack == null:
		return
	var manager := EntityManager.get_instance()
	var player: Entity = manager.get_controlled_entity() if manager != null else null
	if player != null:
		ConnectionManager.activate_skill(_attack.skill_id, _level, player.get_logical_position())
	_attack = null
