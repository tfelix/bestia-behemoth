extends MouseState
class_name MouseStateDefault

## Nothing special active: a ground click walks, and a click on something does whatever
## [method _action_for] says that thing is for. Right-click opens the context menu.
##
## The cursor is driven from that same [method _action_for], so what the pointer promises and what the
## button delivers cannot come apart.


func enter(mgr: MouseManager) -> void:
	mgr.set_cursor_for_action(_action_for(mgr.hovered_object))


func handle_object_clicked(mgr: MouseManager, object: Node3D, event: InputEvent, _click_position: Vector3) -> void:
	if not event.is_action_pressed("normal_action"):
		return

	# Clicking a creature is how you look at it, whether or not the click also does something.
	if object is BestiaVisual or object is MasterVisual:
		mgr.select_entity(object)

	match _action_for(object):
		DefaultAction.Kind.ATTACK:
			# Walked into reach first, then committed to: the server keeps swinging at it until one of them
			# dies or we walk away. The request supersedes any pending goal itself.
			mgr.request_attack(object, object.get_bestia_entity_id())

		DefaultAction.Kind.TALK:
			# Walked up to first, unlike a swing: you have to be in earshot.
			mgr.request_interact(object, object.get_bestia_entity_id())

		DefaultAction.Kind.LOOT:
			mgr.cancel_steering()
			ConnectionManager.loot_item(object.get_item_entity_id())

		DefaultAction.Kind.COLLECT, DefaultAction.Kind.MINE:
			# Sends now if we are already close, otherwise walks there first. Both requests supersede any
			# pending goal themselves, so clicking a second thing simply retargets.
			mgr.request_collect(object)

		DefaultAction.Kind.CHOP:
			# Felling is damage: PropPromotionService gives the prop Health off its prop-kinds.yml max-hp
			# the first time something names it as a target, so this is the same order as a swing, and keeps
			# going by itself until the tree is down.
			mgr.request_attack(object, object.entity_id)

		DefaultAction.Kind.USE_STATION:
			mgr.request_interact(object, object.entity_id)

		DefaultAction.Kind.BUILD:
			# A construction site: walking up to it and clicking is how you start building, and how you stop.
			mgr.request_interact(object, object.get_structure_entity_id())


func handle_object_hover(mgr: MouseManager, _object: Node3D, _entered: bool) -> void:
	# Read back what the manager settled on instead of trusting this one event: sliding from one target
	# straight onto another delivers the second's enter before the first's exit, and honouring that exit
	# would blank a cursor that had already changed.
	mgr.set_cursor_for_action(_action_for(mgr.hovered_object))


func handle_ground_input_event(mgr: MouseManager, click_position: Vector3, event: InputEvent) -> void:
	if event.is_action_pressed("normal_action"):
		# An explicit walk order replaces whatever we were walking towards - the pilot is only ever the
		# client steering, and so stops the moment the player steers themselves.
		mgr.cancel_steering()
		ConnectionManager.move_to(click_position)


func handle_right_click(mgr: MouseManager, screen_position: Vector2) -> void:
	var target := mgr.hovered_object
	if not is_instance_valid(target):
		return

	# Our own body is under the cursor as often as anyone else's, and there is nothing to do to it.
	if target.has_method("get_bestia_entity_id") and target.get_bestia_entity_id() == mgr.own_entity_id:
		return

	mgr.open_context_menu_for(target, screen_position)


## What a left-click on [param object] would do. Null - nothing hovered - is [constant
## DefaultAction.Kind.NONE], so callers do not have to guard.
func _action_for(object: Node3D) -> int:
	if object is BestiaVisual:
		return _action_for_bestia(object as BestiaVisual)

	if object is MasterVisual:
		# Selecting only. Clicking another player is how you look at them, not how you hit them - what you
		# can do to them lives in the right-click menu.
		return DefaultAction.Kind.SELECT

	if object is ItemVisual:
		return DefaultAction.Kind.LOOT

	if object is PropPicker:
		# Per static kind, decided in PropAppearance - the table that already says what each prop is for.
		var prop_action := DefaultAction.from_prop_name(object.action)

		# A kind that mirrored prop-kinds.yml's collect block but was never given an action of its own still
		# gets picked up. StaticEntityRenderer builds it a target on the same grounds, and a target that
		# ignores clicks would be worse than no target at all.
		if prop_action == DefaultAction.Kind.NONE and object.collectible:
			return DefaultAction.Kind.COLLECT

		return prop_action

	if object is StructureVisual:
		return DefaultAction.Kind.BUILD

	return DefaultAction.Kind.NONE


## Whether clicking a creature means hitting it or speaking to it, from the one place that decides
## disposition.
func _action_for_bestia(visual: BestiaVisual) -> int:
	var entity_manager := EntityManager.get_instance()
	var entity := entity_manager.get_entity(visual.get_bestia_entity_id())

	if entity != null and entity_manager.is_entity_friendly(entity):
		return DefaultAction.Kind.TALK

	return DefaultAction.Kind.ATTACK
