class_name SpellTrainingUse
extends PickerCastUse

## Spell Training asks which held spell scroll to teach before the bestia is aimed. The cast's arg is the
## scroll's unique id.


func _open_picker(attack: AttackResource, level: int) -> void:
	var scroll_ids := _held_scrolls()
	SpellScrollBook.ask(scroll_ids, func() -> void: _show_scrolls(attack, level, scroll_ids))


func _show_scrolls(attack: AttackResource, level: int, scroll_ids: Array[int]) -> void:
	var menu := PopupMenu.new()
	for unique_id in scroll_ids:
		var spell := SpellScrollBook.describe(unique_id)
		if not spell.is_empty():
			menu.add_item(spell)
			menu.set_item_metadata(menu.item_count - 1, unique_id)

	if menu.item_count == 0:
		menu.add_item(tr("SPELL_SCROLL_NONE"))
		menu.set_item_disabled(0, true)

	menu.index_pressed.connect(
		func(index: int) -> void: _picked(attack, level, PackedInt64Array([menu.get_item_metadata(index)]))
	)
	_show_menu(menu)


func _held_scrolls() -> Array[int]:
	var scroll_ids: Array[int] = []
	var inventory = (Engine.get_main_loop() as SceneTree).get_first_node_in_group("inventory")
	if inventory == null:
		return scroll_ids

	for held in inventory.held_instances():
		if held.item.item_id == SpellScrollBook.ITEM_ID:
			scroll_ids.append(held.unique_id)

	return scroll_ids

