extends Node
class_name MouseManager

## Mouse interaction state machine for the game scene. Holds the active MouseState
## (default / item targeting / skill targeting) and routes discrete clicks,
## hover, and per-frame updates into it. Objects report their own physics-
## picking events here instead of acting on them directly, so what a click
## actually does only lives in one place (the current state).
##
## A node under [code]Game[/code] rather than an autoload: the selection, what the cursor is over and
## our own entity id are all world state that stops meaning anything the moment the world does.
## Callers find it with [method get_instance], the same way they find [EntityManager].

const _FLOOR_GROUP: String = "floor"
const _GROUP: String = "mouse_manager"
const _UI_PANEL_GROUP: String = "world_blocking_ui"
const _ContextMenuScene := preload("res://Game/UI/ContextMenu/ContextMenu.tscn")

## Emitted whenever the selected entity changes, carrying the newly selected entity's
## id (0 if the selection was cleared). UI panels that show per-entity state (e.g.
## BuffList) listen to this instead of polling selected_entity every frame.
signal entity_selected(entity_id: int)

var current_state: MouseState
var selected_entity: Node3D = null

## Whatever the cursor is currently over, or null. A right-click arrives as a screen position only, so this
## is what tells the context menu what it is being opened on - re-raycasting would have to agree with physics
## picking about which of two overlapping bodies won, and it would not always.
var hovered_object: Node3D = null

## Our own master's entity id, so the menu never offers to trade with ourselves. Cached off SelfSMSG, the
## same way BuffList, Inventory and Equipment cache it.
var own_entity_id: int = 0

var _context_menu: ContextMenu = null

## What the cursor currently names, and whether the button is down - the two inputs to _apply_cursor.
var _hover_action: int = DefaultAction.Kind.NONE
var _pressed: bool = false

## Whether the cursor is the hovered target's, as opposed to art a targeting state set for itself. Only
## the first kind follows the button; the second would otherwise be swapped out on the next press.
var _cursor_follows_action: bool = true

## What is actually on the OS cursor, so that _set_os_cursor can skip a repeat.
var _applied_texture: Texture2D = null
var _applied_hotspot := Vector2.ZERO


## The one in the current scene, or null before [code]Game.tscn[/code] exists, so guard the result.
static func get_instance() -> MouseManager:
	var loop := Engine.get_main_loop() as SceneTree
	return loop.get_first_node_in_group(_GROUP) as MouseManager


## Joins the group here rather than in _ready(), for the reason [EntityManager] documents: Godot
## propagates _enter_tree() across the whole subtree before it runs a single _ready(), so a sibling
## declared ahead of us in Game.tscn is fully ready while our own _ready() has yet to fire.
func _enter_tree() -> void:
	add_to_group(_GROUP)


## The cursor is the OS's again once the world is gone: MouseManager dies with Game.tscn, and Godot does
## not put a custom cursor back by itself - the menus would inherit whatever was last hovered.
func _exit_tree() -> void:
	reset_os_cursor()


func _ready() -> void:
	current_state = MouseStateDefault.new()
	current_state.enter(self)
	ConnectionManager.self_received.connect(_on_self_received)


func _process(delta: float) -> void:
	current_state.process_state(self, delta)

	# Polled rather than read off an event: physics picking delivers the press through input_event, but a
	# release after the mouse has slid off the collider is not promised to come back to the same node - and
	# a cursor stuck closed is worse than a frame of latency. _apply_cursor no-ops unless the art changed.
	var pressed := Input.is_action_pressed("normal_action")
	if pressed != _pressed:
		_pressed = pressed
		if _cursor_follows_action:
			_apply_cursor()


func _unhandled_input(event: InputEvent) -> void:
	if event.is_action_pressed("ui_cancel"):
		current_state.handle_cancel(self)
		return

	current_state.handle_input(self, event)


func change_state(new_state: MouseState) -> void:
	# Deliberately does not stop the walk. Every cast enters and leaves targeting through here, so
	# cancelling would mean a hotbar key ended a journey - and the orders that really do supersede
	# movement (attack, loot, a ground click) cancel it themselves. See MouseStateDefault.
	current_state.exit(self)
	current_state = new_state
	current_state.enter(self)


## Collects a prop, walking to it first if it is out of reach.
func request_collect(picker: PropPicker) -> void:
	MovementPilot.get_instance().collect(picker)


## Walks up to [param target] and interacts with it. Supersedes any pending goal itself, so clicking a second
## thing simply retargets.
func request_interact(target: Node3D, entity_id: int) -> void:
	MovementPilot.get_instance().interact(target, entity_id)


## Commits to attacking [param target], walking into reach first if need be. Supersedes any pending goal
## itself, so clicking a second thing simply retargets.
func request_attack(target: Node3D, entity_id: int) -> void:
	MovementPilot.get_instance().attack(target, entity_id)


## Abandons whatever the pilot was walking towards. Named for what it does rather than for what asks
## for it, because a journey and a walk to a prop end the same way and through the same call.
func cancel_steering() -> void:
	MovementPilot.get_instance().cancel()


func enter_default() -> void:
	change_state(MouseStateDefault.new())


## [param ghost] is a preview node to hold under the cursor, for an item that is placed rather than simply
## aimed. The state takes ownership of it, snaps it to tile centres, and lets the player turn it.
func enter_item_targeting(
	item: ItemResource, item_use: ItemUse, cursor_texture: Texture2D = null, ghost: Node3D = null
) -> void:
	print_debug("MouseManager.enter_item_targeting: %s" % [item.name])
	var state := MouseStateItemTargeting.new()
	state.item = item
	state.item_use = item_use
	state.cursor_texture = cursor_texture
	state.ghost = ghost
	change_state(state)


func enter_skill_targeting(skill: AttackResource, skill_level: int, indicator_scene: PackedScene = null) -> void:
	print_debug("MouseManager.enter_skill_targeting: %s" % [skill.name])
	var state := MouseStateSkillTargeting.new()
	state.skill = skill
	state.skill_level = skill_level
	if indicator_scene:
		state.indicator_scene = indicator_scene
	change_state(state)


func is_targeting() -> bool:
	return current_state is MouseStateItemTargeting or current_state is MouseStateSkillTargeting


## Whether the player is holding a placement ghost. The camera skips its wheel zoom while this is true, so
## that the wheel turns what is being placed - asking here rather than racing for the event, the way the
## right-click handling already does.
func is_placing() -> bool:
	return current_state is MouseStateItemTargeting and current_state.has_ghost()


## Whether the cursor sits on a panel - something the player is reading, rather than ground they are
## pointing at. Panels join [code]world_blocking_ui[/code] themselves; what is merely drawn over the world
## (clock, buffs) stays out of it, because the ground behind those is still the player's to click.
##
## Rect-tested rather than asked of [method Viewport.gui_get_hovered_control]: the HUD sits in a full-screen
## Control that passes input on, so the viewport names a hovered control wherever the mouse is.
func is_pointer_over_ui() -> bool:
	# A captured mouse is not over anything: its reported position sticks near the screen centre, which is
	# where the windows open - so an open window would otherwise freeze the zoom for a whole camera drag.
	if Input.mouse_mode == Input.MOUSE_MODE_CAPTURED:
		return false

	for node in get_tree().get_nodes_in_group(_UI_PANEL_GROUP):
		var panel := node as Control
		if panel == null or not panel.is_visible_in_tree():
			continue
		if panel.get_global_rect().has_point(panel.get_global_mouse_position()):
			return true

	return false


## Called by camera_spring_arm.gd when RMB is pressed while a targeting mode
## is active, instead of the usual camera-drag/context-menu handling.
func cancel_targeting() -> void:
	if is_targeting():
		current_state.handle_cancel(self)


## Whether a physics-picking event is a click at all. [signal CollisionObject3D.input_event] also fires
## for mouse *motion* over the collider, once per frame, so every emitter filters with this before
## calling [method object_clicked] - hovering a prop would otherwise run the click path at frame rate.
static func is_click_event(event: InputEvent) -> bool:
	return event is InputEventMouseButton


## Routes a click on [param object] into the current state. Only ever reached with a button event, so a
## state only has to decide *which* button it cares about.
func object_clicked(object: Node3D, event: InputEvent, click_position: Vector3) -> void:
	print_debug("object_clicked: object: %s" % [object.name])
	current_state.handle_object_clicked(self, object, event, click_position)


func on_object_hover(object: Node3D, entered: bool) -> void:
	if entered:
		hovered_object = object
	elif hovered_object == object:
		hovered_object = null

	current_state.handle_object_hover(self, object, entered)


func on_ground_input_event(position: Vector3, event: InputEvent) -> void:
	# print_debug("on_ground_input_event: position: %s" % [position])
	current_state.handle_ground_input_event(self, position, event)


## Called by camera_spring_arm.gd on a "clean" right-click (press+release with
## no drag) that wasn't already consumed by cancel_targeting().
func right_clicked(screen_position: Vector2) -> void:
	current_state.handle_right_click(self, screen_position)


## Opens the context menu on [param target]. Nothing is shown when that target offers no actions, so the
## caller does not have to know what is actionable.
func open_context_menu_for(target: Node3D, screen_position: Vector2) -> void:
	if target == null:
		return

	if _context_menu == null:
		_context_menu = _ContextMenuScene.instantiate()
		add_child(_context_menu)

	_context_menu.open_for(target, screen_position)


func _on_self_received(msg: SelfSMSG) -> void:
	own_entity_id = msg.MasterEntityId


func select_entity(entity: Node3D) -> void:
	if selected_entity and is_instance_valid(selected_entity) and selected_entity.has_method("set_selected"):
		selected_entity.set_selected(false)
	selected_entity = entity
	if selected_entity and selected_entity.has_method("set_selected"):
		selected_entity.set_selected(true)
	entity_selected.emit(_get_selected_entity_id())


## Visual nodes (BestiaVisual, MasterVisual, ...) expose the entity id they belong
## to via get_bestia_entity_id() - see BestiaVisual.get_bestia_entity_id(). Falls
## back to 0 ("no entity"), the same sentinel used across entity_manager.gd.
func _get_selected_entity_id() -> int:
	# Guarded, unlike the has_method() alone: nothing clears the selection when its entity vanishes, so a
	# killed target leaves a freed node here until something else is picked.
	if is_instance_valid(selected_entity) and selected_entity.has_method("get_bestia_entity_id"):
		return selected_entity.get_bestia_entity_id()
	return 0


## Puts art of the caller's choosing on the cursor and leaves it there - for a targeting state that aims
## with its own icon. A hovered target's cursor goes through [method set_cursor_for_action] instead.
func set_os_cursor(texture: Texture2D, hotspot: Vector2 = Vector2.ZERO) -> void:
	_cursor_follows_action = false
	_set_os_cursor(texture, hotspot)


## Hands the pointer back to the OS. For leaving the world - a menu, a loading screen - not for leaving
## a target: in game the cursor over nothing in particular is DefaultAction.Kind.NONE, a finger.
func reset_os_cursor() -> void:
	set_os_cursor(null)


## Shows what a left-click on the hovered target would do. See DefaultAction.
func set_cursor_for_action(action: int) -> void:
	_hover_action = action
	_cursor_follows_action = true
	_apply_cursor()


func _apply_cursor() -> void:
	var cursor := MouseCursor.resolve(_hover_action, _pressed)
	_set_os_cursor(cursor[&"texture"], cursor[&"hotspot"])


## Skipped when nothing changed: Input.set_custom_mouse_cursor rebuilds the cursor on every call, which
## is not something to do at frame rate.
func _set_os_cursor(texture: Texture2D, hotspot: Vector2) -> void:
	if texture == _applied_texture and hotspot == _applied_hotspot:
		return

	_applied_texture = texture
	_applied_hotspot = hotspot
	Input.set_custom_mouse_cursor(texture, Input.CURSOR_ARROW, hotspot)


## Per-frame camera ray against the "floor" group, used by targeting states
## to keep a cast/placement indicator tracking the mouse smoothly. Discrete
## clicks don't need this - they get their world position for free from the
## physics-picking input_event signal on the clicked object.
func get_floor_hit_at_mouse() -> Variant:
	# While the camera has captured the mouse (RMB drag to rotate), the OS
	# cursor is hidden and its reported position no longer follows the real
	# mouse - it sticks near screen center, which would raycast right next
	# to the character. Every floor-tracking indicator shares this function,
	# so suppressing it here hides them all instead of patching each caller.
	if Input.mouse_mode == Input.MOUSE_MODE_CAPTURED:
		return null

	# A panel under the cursor is what is being pointed at, not the ground behind it. Same argument as
	# above: every floor-tracking indicator comes through here, so one return hides them all.
	if is_pointer_over_ui():
		return null

	var viewport := get_viewport()
	var camera := viewport.get_camera_3d()
	if camera == null:
		return null

	var mouse_pos := viewport.get_mouse_position()
	var from := camera.project_ray_origin(mouse_pos)
	var to := from + camera.project_ray_normal(mouse_pos) * camera.far
	var space_state := camera.get_world_3d().direct_space_state
	var query := PhysicsRayQueryParameters3D.create(from, to)
	var result := space_state.intersect_ray(query)

	if result.is_empty():
		return null

	var collider = result["collider"]
	if collider is Node and collider.is_in_group(_FLOOR_GROUP):
		return result["position"]
	return null
