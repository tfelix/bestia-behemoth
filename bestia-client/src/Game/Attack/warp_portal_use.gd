class_name WarpPortalUse
extends PickerCastUse

## Warp Portal asks where the portal leads before it is aimed: home, or one of the spots the Sage saved. Marking
## the spot the Sage stands on is offered too; it is free and goes to the server at once, without aiming.
## The numbers are the server's: 0 marks, 1 is home, 2 and up are the saved spots, oldest first.

const _MARK := 0
const _HOME := 1
const _FIRST_SPOT := 2


func _open_picker(attack: AttackResource, level: int) -> void:
	var menu := PopupMenu.new()
	menu.add_item(tr("PORTAL_MARK_SPOT") % [EntityManager.get_instance().get_warp_marks().size(), level], _MARK)
	menu.add_separator()
	menu.add_item(tr("PORTAL_TO_HOME"), _HOME)

	var here := _here()
	var spots := EntityManager.get_instance().get_warp_marks()
	for i in spots.size():
		var away := Vector2(spots[i].x - here.x, spots[i].z - here.z)
		var bearing := CompassStrip.bearing_between(here, spots[i])
		menu.add_item(tr("PORTAL_TO_SPOT") % [i + 1, CompassStrip.name_of(bearing), away.length() / 1000.0], _FIRST_SPOT + i)

	menu.id_pressed.connect(func(choice: int) -> void: _chosen(attack, level, choice))
	menu.popup_hide.connect(menu.queue_free)

	var tree := Engine.get_main_loop() as SceneTree
	tree.root.add_child(menu)
	menu.popup(Rect2i(Vector2i(tree.root.get_mouse_position()), Vector2i.ZERO))


func _chosen(attack: AttackResource, level: int, choice: int) -> void:
	if choice == _MARK:
		ConnectionManager.activate_skill(attack.skill_id, level, Vector3.ZERO, 0, PackedInt64Array([_MARK]))
		return
	_picked(attack, level, PackedInt64Array([choice]))


func _here() -> Vector3:
	var master := EntityManager.get_instance().get_master_entity()
	return master.global_position if master != null else Vector3.ZERO
