class_name ScryUse
extends PickerCastUse

## Scry asks what to look at: a party member, or a spot picked on the map, where "Scry here" casts it. While a
## sight is open the button is the way back to the body instead.

const _MAP := -1


func on_skill_activated(attack: AttackResource, level: int) -> void:
	var scry := ScryView.get_instance()
	if scry != null and scry.is_open():
		scry.return_to_body()
		return
	super.on_skill_activated(attack, level)


func _open_picker(_attack: AttackResource, _level: int) -> void:
	var scry := ScryView.get_instance()
	if scry == null:
		return

	var menu := PopupMenu.new()
	menu.add_item(tr("SCRY_PICK_ON_MAP"), _MAP)
	var members := scry.party_members()
	if not members.is_empty():
		menu.add_separator()
	for master_id in members:
		menu.add_item(tr("SCRY_PARTY_MEMBER") % members[master_id], master_id)

	menu.id_pressed.connect(func(choice: int) -> void: _chosen(scry, choice))
	_show_menu(menu)


func _chosen(scry: ScryView, choice: int) -> void:
	if choice == _MAP:
		scry.map_requested.emit()
	else:
		scry.scry_member(choice)
