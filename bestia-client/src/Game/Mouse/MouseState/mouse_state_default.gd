extends MouseState
class_name MouseStateDefault

## Nothing special active: a ground click walks, and a click on something does whatever
## [method _action_for] says that thing is for. Right-click opens the context menu.
##
## The cursor is driven from that same [method _action_for], so what the pointer promises and what the
## button delivers cannot come apart.


func enter(mgr: MouseManager) -> void:
	mgr.set_cursor_for_action(_action_for(_click_target(mgr, mgr.hovered_object)))


func process_state(mgr: MouseManager, _delta: float) -> void:
	# Sliding the cursor down a creature onto the loot at its feet crosses no collider edge, so no hover event
	# says the click target changed. Looked up every frame instead - but only while a creature is hovered.
	if _is_creature(mgr.hovered_object):
		mgr.set_cursor_for_action(_action_for(_click_target(mgr, mgr.hovered_object)))


func handle_object_clicked(mgr: MouseManager, object: Node3D, event: InputEvent, _click_position: Vector3) -> void:
	if not event.is_action_pressed("normal_action"):
		return

	object = _click_target(mgr, object)

	# Clicking a creature is how you look at it, whether or not the click also does something.
	if object is BestiaVisual or object is MasterVisual:
		mgr.select_entity(object)

	match _action_for(object):
		DefaultAction.Kind.ATTACK:
			# Walked into reach first, then committed to: the server keeps swinging at it until one of them
			# dies or we walk away. The request supersedes any pending goal itself.
			var is_structure: bool = object is StructureVisual
			var target_id: int = object.get_structure_entity_id() if is_structure else object.get_bestia_entity_id()
			mgr.request_attack(object, target_id)

		DefaultAction.Kind.TALK:
			# Walked up to first, unlike a swing: you have to be in earshot.
			mgr.request_interact(object, object.get_bestia_entity_id())

		DefaultAction.Kind.LOOT:
			# Walked up to first: the server only hands over what lies next to us.
			mgr.request_loot(object, object.get_item_entity_id())

		DefaultAction.Kind.COLLECT, DefaultAction.Kind.MINE:
			# Sends now if we are already close, otherwise walks there first. Both requests supersede any
			# pending goal themselves, so clicking a second thing simply retargets.
			mgr.request_collect(object)

		DefaultAction.Kind.CHOP, DefaultAction.Kind.BREAK:
			# Felling is damage: PropPromotionService gives the prop Health off its prop-kinds.yml max-hp
			# the first time something names it as a target, so this is the same order as a swing, and keeps
			# going by itself until the tree is down.
			mgr.request_attack(object, object.entity_id)

		DefaultAction.Kind.USE_STATION:
			# A finished station is a prop, a stall an entity: each carries its id under its own name.
			var target_id: int = object.get_structure_entity_id() if object is StructureVisual else object.entity_id
			mgr.request_interact(object, target_id)

		DefaultAction.Kind.BUILD:
			# A construction site: walking up to it and clicking is how you start building, and how you stop.
			mgr.request_interact(object, object.get_structure_entity_id())

		DefaultAction.Kind.STRIKE:
			ConnectionManager.strike_line()


func handle_object_hover(mgr: MouseManager, _object: Node3D, _entered: bool) -> void:
	# Read back what the manager settled on instead of trusting this one event: sliding from one target
	# straight onto another delivers the second's enter before the first's exit, and honouring that exit
	# would blank a cursor that had already changed.
	mgr.set_cursor_for_action(_action_for(_click_target(mgr, mgr.hovered_object)))


func handle_ground_input_event(mgr: MouseManager, click_position: Vector3, event: InputEvent) -> void:
	# The ground under the mouse is the far spot's: the body would set off towards it, and the step end the sight.
	var scry := ScryView.get_instance()
	if scry != null and scry.is_open():
		return

	if event.is_action_pressed("normal_action"):
		# An explicit walk order replaces whatever we were walking towards - the pilot is only ever the
		# client steering, and so stops the moment the player steers themselves. A cast is an order too.
		mgr.cancel_steering()
		if not Angler.try_cast(click_position):
			ConnectionManager.move_to(click_position)


func handle_right_click(mgr: MouseManager, screen_position: Vector2) -> void:
	var target := mgr.hovered_object
	if not is_instance_valid(target):
		return

	# The body we drive is under the cursor as often as anyone else's, and there is nothing to do to it. Our
	# other bodies offer to swap goods with it.
	var entity_manager := EntityManager.get_instance()
	var is_driven: bool = entity_manager != null and target.has_method("get_bestia_entity_id") \
			and target.get_bestia_entity_id() == entity_manager.get_controlled_entity_id()
	if is_driven:
		return

	mgr.open_context_menu_for(target, screen_position)


## What a left-click on [param object] actually lands on. Loot wins over a creature standing on it - our own
## body above all, since we stand on what we are about to pick up - unless the click would attack that
## creature: a swing at what we are fighting must not turn into a walk to its drop.
func _click_target(mgr: MouseManager, object: Node3D) -> Node3D:
	if not _is_creature(object) or _action_for(object) == DefaultAction.Kind.ATTACK:
		return object

	var loot := mgr.find_loot_behind_creatures()
	if loot != null:
		return loot
	return object


## Null-safe, for nothing hovered. A freed [member MouseManager.hovered_object] never gets this far: the
## manager drops it before the frame's state runs, since the typed parameter alone would already fail on it.
func _is_creature(object: Node3D) -> bool:
	return is_instance_valid(object) and (object is BestiaVisual or object is MasterVisual)


## What a left-click on [param object] would do. Null - nothing hovered - is [constant
## DefaultAction.Kind.NONE], so callers do not have to guard.
func _action_for(object: Node3D) -> int:
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

	if object is Bobber:
		return DefaultAction.Kind.STRIKE if object.is_own() else DefaultAction.Kind.NONE

	if object is StructureVisual and object.is_construction_site():
		return DefaultAction.Kind.BUILD

	if _is_creature(object):
		return _action_for_creature(object as Visual)

	if object is Visual:
		return _action_for_visual(object as Visual)

	return DefaultAction.Kind.NONE


## What a non-creature visual's designer chose for a click. A structure that names no choice still does what
## PropAppearance says its kind is for, as stations, stalls and the city sign do.
func _action_for_visual(visual: Visual) -> int:
	match visual.default_click_action:
		Visual.ClickAction.SELECT:
			return DefaultAction.Kind.SELECT
		Visual.ClickAction.INTERACT:
			return DefaultAction.Kind.USE_STATION
		Visual.ClickAction.ATTACK:
			return DefaultAction.Kind.ATTACK

	if visual is StructureVisual:
		if visual.is_usable():
			return DefaultAction.Kind.USE_STATION

		var structure_action := DefaultAction.from_prop_name(visual.action)
		return structure_action if structure_action != DefaultAction.Kind.NONE else DefaultAction.Kind.SELECT

	return DefaultAction.Kind.NONE


## A creature's designer chose fight, talk or look. Who owns it and how it stands towards us right now can
## still turn a swing into talk or a look, from the one place that decides disposition.
func _action_for_creature(visual: Visual) -> int:
	var entity_manager := EntityManager.get_instance()
	var entity := entity_manager.get_entity(visual.get_bestia_entity_id())

	# Already gone from the world - a mob playing its death on the way out - so there is nothing left to do to it.
	if entity == null:
		return DefaultAction.Kind.NONE

	# One of our own: switching to it is the party panel's job, so a click only selects it.
	if entity_manager.is_owned_entity_id(visual.get_bestia_entity_id()):
		return DefaultAction.Kind.SELECT

	match visual.default_click_action:
		Visual.ClickAction.SELECT:
			return DefaultAction.Kind.SELECT
		Visual.ClickAction.INTERACT:
			return DefaultAction.Kind.TALK
		Visual.ClickAction.ATTACK:
			if entity_manager.is_entity_friendly(entity):
				return DefaultAction.Kind.TALK
			# Warded or dead: the server would refuse the blow, so a click only looks.
			if not entity_manager.can_be_harmed(entity):
				return DefaultAction.Kind.SELECT
			return DefaultAction.Kind.ATTACK

	return DefaultAction.Kind.NONE
