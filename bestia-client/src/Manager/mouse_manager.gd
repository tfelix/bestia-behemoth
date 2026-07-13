extends Node

## Scene-global mouse interaction state machine. Holds the active MouseState
## (default / item targeting / skill targeting) and routes discrete clicks,
## hover, and per-frame updates into it. Objects report their own physics-
## picking events here instead of acting on them directly, so what a click
## actually does only lives in one place (the current state).

const _FLOOR_GROUP: String = "floor"
const _ContextMenuScene := preload("res://Game/UI/ContextMenu/ContextMenu.tscn")

## Emitted whenever the selected entity changes, carrying the newly selected entity's
## id (0 if the selection was cleared). UI panels that show per-entity state (e.g.
## BuffList) listen to this instead of polling selected_entity every frame.
signal entity_selected(entity_id: int)

var current_state: MouseState
var selected_entity: Node3D = null
var _context_menu: PopupMenu = null


func _ready() -> void:
	current_state = MouseStateDefault.new()
	current_state.enter(self)


func _process(delta: float) -> void:
	current_state.process_state(self, delta)


func _unhandled_input(event: InputEvent) -> void:
	if event.is_action_pressed("ui_cancel"):
		current_state.handle_cancel(self)


func change_state(new_state: MouseState) -> void:
	current_state.exit(self)
	current_state = new_state
	current_state.enter(self)


func enter_default() -> void:
	change_state(MouseStateDefault.new())


func enter_item_targeting(item: ItemResource, item_use: ItemUse, cursor_texture: Texture2D = null) -> void:
	print_debug("MouseManager.enter_item_targeting: %s" % [item.name])
	var state := MouseStateItemTargeting.new()
	state.item = item
	state.item_use = item_use
	state.cursor_texture = cursor_texture
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


## Called by camera_spring_arm.gd when RMB is pressed while a targeting mode
## is active, instead of the usual camera-drag/context-menu handling.
func cancel_targeting() -> void:
	if is_targeting():
		current_state.handle_cancel(self)


func object_clicked(object: Node3D, event: InputEvent, click_position: Vector3) -> void:
	print_debug("object_clicked: object: %s" % [object.name])
	current_state.handle_object_clicked(self, object, event, click_position)


func on_object_hover(object: Node3D, entered: bool) -> void:
	current_state.handle_object_hover(self, object, entered)


func on_ground_input_event(position: Vector3, event: InputEvent) -> void:
	# print_debug("on_ground_input_event: position: %s" % [position])
	current_state.handle_ground_input_event(self, position, event)


## Called by camera_spring_arm.gd on a "clean" right-click (press+release with
## no drag) that wasn't already consumed by cancel_targeting().
func right_clicked(screen_position: Vector2) -> void:
	current_state.handle_right_click(self, screen_position)


func open_context_menu(screen_position: Vector2) -> void:
	if _context_menu == null:
		_context_menu = _ContextMenuScene.instantiate()
		add_child(_context_menu)
	_context_menu.open_at(screen_position)


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
	if selected_entity and selected_entity.has_method("get_bestia_entity_id"):
		return selected_entity.get_bestia_entity_id()
	return 0


func set_os_cursor(texture: Texture2D, hotspot: Vector2 = Vector2.ZERO) -> void:
	Input.set_custom_mouse_cursor(texture, Input.CURSOR_ARROW, hotspot)


func reset_os_cursor() -> void:
	Input.set_custom_mouse_cursor(null)


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
