class_name PotionPitcherUse
extends PickerCastUse

## Potion Pitcher asks which potion from the bag to throw before the ally is aimed. The cast's arg is the potion's
## item id.

const _ICON_WIDTH := 24


func _open_picker(attack: AttackResource, level: int) -> void:
	var menu := PopupMenu.new()
	var inventory = (Engine.get_main_loop() as SceneTree).get_first_node_in_group("inventory")
	for item: ItemResource in ItemDB.get_instance().all_items():
		var held: int = inventory.get_item_count(item.item_id) if inventory != null else 0
		if not item.is_potion or held == 0:
			continue
		menu.add_icon_item(item.get_icon(), "%s (%d)" % [tr(item.name_key), held], item.item_id)
		menu.set_item_icon_max_width(menu.item_count - 1, _ICON_WIDTH)

	if menu.item_count == 0:
		menu.add_item(tr("POTION_PITCHER_NONE"))
		menu.set_item_disabled(0, true)

	menu.id_pressed.connect(func(item_id: int) -> void: _picked(attack, level, PackedInt64Array([item_id])))
	_show_menu(menu)
