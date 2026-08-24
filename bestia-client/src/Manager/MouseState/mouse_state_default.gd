extends MouseState
class_name MouseStateDefault

## Nothing special active: walk on ground click, attack a bestia entity on
## click, loot an item entity on click, collect a static prop on click,
## interact with an Interactable if the clicked/hovered object has one,
## right-click opens the context menu.


func enter(mgr) -> void:
	mgr.reset_os_cursor()


func handle_object_clicked(mgr: MouseManager, object: Node3D, event: InputEvent, click_position: Vector3) -> void:
	if not event.is_action_pressed("normal_action"):
		return

	var entity := _find_entity(object)
	if entity:
		entity.on_interact()
		return

	if object is BestiaVisual:
		# Attacking or looting is a new order and supersedes a walk that was on its way to a prop.
		mgr.cancel_pending_collect()
		mgr.select_entity(object)
		ConnectionManager.send_attack_entity(object.get_bestia_entity_id())
	elif object is MasterVisual:
		# Selecting only. Clicking another player is how you look at them, not how you hit them - what you can
		# do to them lives in the right-click menu.
		mgr.select_entity(object)
	elif object is ItemVisual:
		mgr.cancel_pending_collect()
		ConnectionManager.loot_item(object.get_item_entity_id())
	elif object is PropPicker:
		# Sends now if we are already close, otherwise walks there first. request_collect supersedes any
		# pending collect itself, so clicking a second crystal simply retargets.
		mgr.request_collect(object)


func handle_object_hover(mgr: MouseManager, object: Node3D, entered: bool) -> void:
	var interactable := _find_entity(object)
	if interactable == null:
		return
	if entered:
		mgr.set_os_cursor(interactable.hover_cursor)
	else:
		mgr.reset_os_cursor()


func handle_ground_input_event(mgr: MouseManager, click_position: Vector3, event: InputEvent) -> void:
	if event.is_action_pressed("normal_action"):
		# An explicit walk order replaces whatever we were walking towards.
		mgr.cancel_pending_collect()
		ConnectionManager.move_to(click_position)


func handle_right_click(mgr: MouseManager, screen_position: Vector2) -> void:
	var target := mgr.hovered_object
	if target == null:
		return

	# Our own body is under the cursor as often as anyone else's, and there is nothing to do to it.
	if target.has_method("get_bestia_entity_id") and target.get_bestia_entity_id() == mgr.own_entity_id:
		return

	mgr.open_context_menu_for(target, screen_position)


func _find_entity(object: Node3D) -> Entity:
	# I think this makes no sense. If an entity is clicked find the
	# entity and then call on_interact() on the entity. The visual must
	# then react. 
	return null
