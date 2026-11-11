class_name SpellInscriptionUse
extends PickerCastUse

## Spell Inscription asks the server what it could copy off the Sage's bestias, with both odds, and lists that.
## The pick names the bestia too, so the cast goes out at once with the attack's skill id as its arg.


func _open_picker(attack: AttackResource, level: int) -> void:
	var on_options := _show_options.bind(attack, level)
	if not ConnectionManager.inscription_options_received.is_connected(on_options):
		ConnectionManager.inscription_options_received.connect(on_options, CONNECT_ONE_SHOT)
	ConnectionManager.request_inscription_options()


func _show_options(message: InscriptionOptionsSMSG, attack: AttackResource, level: int) -> void:
	var menu := PopupMenu.new()
	for option in message.Options:
		var copied := AttackDB.get_instance().get_attack(int(option.SkillId))
		if copied == null:
			continue
		menu.add_item(
			tr("SPELL_INSCRIPTION_PICK")
			% [_bestia_name(int(option.BestiaEntityId)), copied.name, option.Level, option.SuccessPercent, option.MindBreakPercent]
		)
		menu.set_item_metadata(menu.item_count - 1, option)

	if menu.item_count == 0:
		menu.add_item(tr("SPELL_INSCRIPTION_NONE"))
		menu.set_item_disabled(0, true)

	menu.index_pressed.connect(func(index: int) -> void: _inscribe(attack, level, menu.get_item_metadata(index)))
	_show_menu(menu)


func _inscribe(attack: AttackResource, level: int, option: InscriptionOptionEntry) -> void:
	ConnectionManager.activate_skill(
		attack.skill_id, level, Vector3.ZERO, int(option.BestiaEntityId), PackedInt64Array([int(option.SkillId)])
	)


func _bestia_name(entity_id: int) -> String:
	var bestia := EntityManager.get_instance().get_entity(entity_id)
	return bestia.get_display_name() if bestia != null else "?"
