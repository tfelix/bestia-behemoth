extends MouseState
class_name MouseStateDefault

## Nothing special active: walk on ground click, attack a bestia entity on
## click, loot an item entity on click, interact with an Interactable if the
## clicked/hovered object has one, right-click opens the context menu.


func enter(mgr) -> void:
	mgr.reset_os_cursor()


func handle_object_clicked(mgr, object: Node3D, event: InputEvent, click_position: Vector3) -> void:
	if not event.is_action_pressed("normal_action"):
		return

	var interactable := _find_interactable(object)
	if interactable:
		interactable.on_interact()
		return

	if object is BestiaVisual:
		mgr.select_entity(object)
		ConnectionManager.send_attack_entity(object.get_bestia_entity_id(), 0, 1)
	elif object is ItemVisual:
		ConnectionManager.loot_item(object.get_item_entity_id())


func handle_object_hover(mgr, object: Node3D, entered: bool) -> void:
	var interactable := _find_interactable(object)
	if interactable == null:
		return
	if entered:
		mgr.set_os_cursor(interactable.hover_cursor)
	else:
		mgr.reset_os_cursor()


func handle_ground_clicked(mgr, click_position: Vector3, event: InputEvent) -> void:
	if event.is_action_pressed("normal_action"):
		ConnectionManager.move_to(click_position)


func handle_right_click(mgr, screen_position: Vector2) -> void:
	mgr.open_context_menu(screen_position)


func _find_interactable(object: Node3D) -> Interactable:
	if object == null:
		return null
	for child in object.get_children():
		if child is Interactable:
			return child
	return null
