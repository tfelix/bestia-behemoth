extends MouseState
class_name MouseStateItemTargeting

## Active while a scripted item is waiting for the player to click something.
## Every discrete click (ground or object) is forwarded to the item's own
## ItemUse script instead of running the default click behavior.
##
## An item that is [i]placed[/i] rather than aimed can hand over a [member ghost] - the art of whatever it
## puts down. It is snapped to tile centres and turned with the mouse wheel, and the facing it ends on
## travels in click_info, because where a thing goes and which way it faces are one decision.

## How far the wheel turns the ghost. Quarter turns, because everything placed is drawn on a square grid
## and nothing so far reads as being at an angle to it.
const _YAW_STEP := PI / 2.0

var item: ItemResource
var item_use: ItemUse
var cursor_texture: Texture2D

## Preview node for a placed item, or null for one that just needs a click. Owned by this state.
var ghost: Node3D = null

var _yaw: float = 0.0
var _last_ground_hit = null


## Whether this is a placement rather than a plain aim. The camera asks, so that the wheel turns the ghost
## instead of zooming - see MouseManager.is_placing.
func has_ghost() -> bool:
	return ghost != null


func enter(mgr: MouseManager) -> void:
	mgr.set_os_cursor(cursor_texture)
	if ghost:
		mgr.add_child(ghost)
		ghost.visible = false


func exit(mgr: MouseManager) -> void:
	mgr.reset_os_cursor()
	if ghost:
		ghost.queue_free()
		ghost = null


func process_state(mgr: MouseManager, _delta: float) -> void:
	if ghost == null:
		return

	# Physics-picking signals only fire on discrete events, so tracking the cursor needs its own per-frame
	# raycast - the same reason MouseStateSkillTargeting has one.
	_last_ground_hit = mgr.get_floor_hit_at_mouse()

	if _last_ground_hit == null:
		ghost.visible = false
		return

	ghost.visible = true
	# The centre of the tile it will actually be placed on, not the raw hit, so what the player sees is
	# where it lands. See TileSpace.
	var centre := TileSpace.tile_centre(TileSpace.world_to_tile(_last_ground_hit))
	ghost.global_position = Vector3(centre.x, _last_ground_hit.y, centre.z)
	ghost.rotation.y = _yaw


func handle_input(_mgr: MouseManager, event: InputEvent) -> void:
	if ghost == null:
		return

	# The zoom actions rather than the raw buttons, so which buttons these are stays in the input map.
	# camera_spring_arm.gd skips its own zoom while a ghost is held - see MouseManager.is_placing.
	if event.is_action_pressed("camera_zoom_in"):
		_yaw = fposmod(_yaw + _YAW_STEP, TAU)
	elif event.is_action_pressed("camera_zoom_out"):
		_yaw = fposmod(_yaw - _YAW_STEP, TAU)


func handle_object_clicked(mgr: MouseManager, object: Node3D, event: InputEvent, click_position: Vector3) -> void:
	_try_confirm(mgr, event, click_position, object)


func handle_ground_input_event(mgr: MouseManager, click_position: Vector3, event: InputEvent) -> void:
	_try_confirm(mgr, event, click_position, null)


func handle_cancel(mgr: MouseManager) -> void:
	if item_use:
		item_use.on_targeting_cancelled(item)
	mgr.enter_default()


func _try_confirm(mgr: MouseManager, event: InputEvent, click_position: Vector3, target: Node3D) -> void:
	if not event.is_action_pressed("normal_action"):
		return
	if item_use == null:
		mgr.enter_default()
		return

	var click_info: Dictionary = {
		"position": click_position,
		"target": target,
		"tile": TileSpace.world_to_tile(click_position),
		"yaw": _yaw,
	}
	var consumed: bool = item_use.on_targeting_click(item, click_info)
	if consumed:
		mgr.enter_default()
