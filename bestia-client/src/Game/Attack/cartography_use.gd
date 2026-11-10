class_name CartographyUse
extends PickerCastUse

## Cartography asks how far to survey before it is cast at the master's theodolite. The level opens the bigger
## surveys; the kilometres go to the server as the cast's only arg.

## Survey kilometres and the Cartography level that opens each, as the server's SurveyTier has them.
const _TIERS := [[1, 1], [3, 3], [5, 5]]


func _open_picker(attack: AttackResource, level: int) -> void:
	var menu := PopupMenu.new()
	for tier in _TIERS:
		var km: int = tier[0]
		var min_level: int = tier[1]
		menu.add_item(tr("SURVEY_TIER") % km, km)
		if level < min_level:
			menu.set_item_disabled(menu.get_item_index(km), true)
			menu.set_item_tooltip(menu.get_item_index(km), tr("SURVEY_TIER_NEEDS_LEVEL") % min_level)

	menu.id_pressed.connect(func(km: int) -> void: _picked(attack, level, PackedInt64Array([km])))
	menu.popup_hide.connect(menu.queue_free)

	var tree := Engine.get_main_loop() as SceneTree
	tree.root.add_child(menu)
	menu.popup(Rect2i(Vector2i(tree.root.get_mouse_position()), Vector2i.ZERO))


func _casts_on_self() -> bool:
	return true
