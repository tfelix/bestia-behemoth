class_name Entity extends Node3D

# Movement Prediction System
# ========================
# This system handles client-side movement prediction for networked entities.
#
# Key Features:
# - Follows server-provided paths at specified speeds (tiles/second)
# - Smoothly corrects to server-authoritative positions when they arrive
# - Handles edge cases like speed changes during movement and new paths
#
# Server Communication:
# - PositionComponent: Authoritative position updates (whole coordinates only)
# - PathComponentSMSG: Array of waypoints to follow
# - SpeedComponentSMSG: Movement speed in tiles per second (1 tile = 1 meter)
#
# The server only sends position updates for whole coordinates (e.g., x:1, y:5),
# never for fractional positions. Client handles smooth movement between tiles.


var BestiaModelScn = preload("res://Game/Entity/Visual/BestiaVisual/BestiaVisual.tscn")
var MasterModelScn = preload("res://Game/Entity/Visual/MasterVisual/MasterVisual.tscn")
var Camera = preload("res://Game/SpringArmCamera/SpringArmCamera.tscn")

var entity_id: int = 0

var _camera: Node3D = null
var _speed: float = 1.5


# Movement prediction system
var _current_path: Array[Vector3] = []
var _path_index: int = 0
var _movement_start_time: float = 0.0
var _movement_start_position: Vector3 = Vector3.ZERO
var _target_position: Vector3 = Vector3.ZERO
var _is_moving: bool = false

# Server reconciliation: rather than snapping/pausing on every authoritative position
# update, small desyncs are ignored outright and larger ones are folded in as a
# decaying visual offset on top of the predicted path position, so the entity keeps
# walking its path instead of jerking or freezing.
var _correction_offset: Vector3 = Vector3.ZERO
var _correction_start_offset: Vector3 = Vector3.ZERO
var _correction_start_time: float = 0.0
var _correcting: bool = false

# Visual facing rotation
var _visual_rotation_start_basis: Basis = Basis.IDENTITY
var _visual_rotation_target_basis: Basis = Basis.IDENTITY
var _visual_rotation_start_time: float = 0.0
var _visual_rotating: bool = false


const _CORRECTION_DURATION: float = 0.4  # Time for a larger desync's visual offset to fade out
const _POSITION_IGNORE_THRESHOLD: float = 0.25  # Desyncs below this (in tiles) are trusted local prediction, not corrected
const _ROTATION_DURATION: float = 0.3  # Time to turn the model to face movement direction
const _VISUAL_NODE_NAME = "Visual"


func _process(delta: float) -> void:
	_update_movement(delta)
	_update_visual_rotation()


func _update_movement(_delta: float) -> void:
	var current_time = Time.get_ticks_msec() / 1000.0  # More reliable time source

	_update_correction_offset(current_time)

	# Handle normal path movement. The correction offset above rides on top of
	# whatever position this computes, it never interrupts or replaces it.
	if _is_moving and _current_path.size() > _path_index and _speed > 0.0:
		var movement_progress = (current_time - _movement_start_time) * _speed
		var distance_to_target = _movement_start_position.distance_to(_target_position)

		if movement_progress >= distance_to_target:
			# Reached current waypoint
			var reached_position = _target_position
			position = reached_position + _correction_offset
			_path_index += 1

			# Check if there are more waypoints
			if _path_index < _current_path.size():
				_start_movement_to_next_waypoint(current_time, reached_position)
			else:
				# Path complete
				_is_moving = false
		else:
			# Move towards target
			var t = movement_progress / distance_to_target if distance_to_target > 0 else 1.0
			position = _movement_start_position.lerp(_target_position, t) + _correction_offset


## Fades [_correction_offset] out over [_CORRECTION_DURATION] so a larger desync nudges
## the rendered position smoothly back in sync instead of snapping to it.
func _update_correction_offset(current_time: float) -> void:
	if not _correcting:
		return

	var progress = (current_time - _correction_start_time) / _CORRECTION_DURATION
	if progress >= 1.0:
		_correction_offset = Vector3.ZERO
		_correcting = false
	else:
		_correction_offset = _correction_start_offset * (1.0 - _ease_out_cubic(progress))


func _start_movement_to_next_waypoint(current_time: float, from_position: Vector3) -> void:
	if _path_index >= _current_path.size():
		_is_moving = false
		return

	_movement_start_position = from_position
	_target_position = _current_path[_path_index]
	_movement_start_time = current_time
	_is_moving = true
	_face_direction(_target_position - _movement_start_position)


func _face_direction(direction: Vector3) -> void:
	# Only yaw the model towards the movement direction, ignore any vertical
	# component so it doesn't pitch up/down on sloped waypoint segments.
	var flat_direction = Vector3(direction.x, 0.0, direction.z)
	if flat_direction.length_squared() < 0.0001:
		return

	var visual = get_node_or_null(_VISUAL_NODE_NAME)
	if visual == null:
		return

	# use_model_front = true since the model's forward axis is +Z, not Godot's default -Z.
	var target_basis = Basis.looking_at(flat_direction, Vector3.UP, true)
	_visual_rotation_start_basis = visual.transform.basis
	_visual_rotation_target_basis = target_basis
	_visual_rotation_start_time = Time.get_ticks_msec() / 1000.0
	_visual_rotating = true


func _update_visual_rotation() -> void:
	if not _visual_rotating:
		return

	var visual = get_node_or_null(_VISUAL_NODE_NAME)
	if visual == null:
		_visual_rotating = false
		return

	var current_time = Time.get_ticks_msec() / 1000.0
	var progress = (current_time - _visual_rotation_start_time) / _ROTATION_DURATION

	if progress >= 1.0:
		visual.transform.basis = _visual_rotation_target_basis
		_visual_rotating = false
	else:
		visual.transform.basis = _visual_rotation_start_basis.slerp(_visual_rotation_target_basis, _ease_out_cubic(progress))


func show_chat(msg: ChatSMSG) -> void:
	var visual = _get_visual_for_method("show_chat")
	if visual != null:
		visual.show_chat(msg)
	else:
		printerr("Entity %s has no show_chat visual", [entity_id])


func update_bestia_visual(msg: BestiaVisualComponent) -> void:
	var existing = get_node_or_null(_VISUAL_NODE_NAME)
	if existing != null:
		existing.queue_free()
	var visual = BestiaModelScn.instantiate() as BestiaVisual
	visual.setup_visual(msg)
	visual.name = _VISUAL_NODE_NAME
	add_child(visual)


func update_item_visual(msg: ItemVisualComponentSMSG) -> void:
	var existing = get_node_or_null(_VISUAL_NODE_NAME)
	if existing != null:
		existing.queue_free()
	var item_resource = ItemDB.get_instance().get_item(msg.ItemId)
	if item_resource == null or item_resource.item_visual == null:
		printerr("Entity %s: no item_visual PackedScene for item %s" % [entity_id, msg.ItemId])
		return
	var visual = item_resource.item_visual.instantiate() as ItemVisual
	visual.setup_visual(msg)
	visual.name = _VISUAL_NODE_NAME
	add_child(visual)


func update_master_visual(msg: MasterVisualComponentSMSG) -> void:
	var existing = get_node_or_null(_VISUAL_NODE_NAME)
	if existing != null:
		existing.queue_free()
	var visual = MasterModelScn.instantiate() as MasterVisual
	visual.setup_visual(msg)
	visual.name = _VISUAL_NODE_NAME
	add_child(visual)


func set_selected(is_selected: bool) -> void:
	print("Entity: set_selected: %s" % [is_selected])


func update_position(msg: PositionComponent) -> void:
	var new_position = msg.Position

	if not _is_moving:
		# Nothing to preserve continuity with, just snap and clear any leftover offset.
		position = new_position
		_correction_offset = Vector3.ZERO
		_correcting = false
		return

	# _correction_offset is a decaying nudge on top of the path-predicted position (see
	# _update_correction_offset), so subtracting it back out recovers that pure prediction.
	var predicted_position = position - _correction_offset
	var error = predicted_position.distance_to(new_position)

	if error <= _POSITION_IGNORE_THRESHOLD:
		# Small desync: trust our own prediction rather than correcting every tiny drift.
		return

	# Larger desync: fold it into a decaying offset instead of snapping/pausing, so the
	# entity keeps walking its current path while the visual position eases back in sync.
	_correction_start_offset = new_position - predicted_position
	_correction_start_time = Time.get_ticks_msec() / 1000.0
	_correcting = true


func update_path(msg: PathComponentSMSG) -> void:
	# Convert C# path to Godot Vector3 array
	_current_path.clear()
	for vec3 in msg.Path:
		_current_path.append(vec3)

	# Start movement if we have waypoints
	if _current_path.size() > 0:
		var current_time = Time.get_ticks_msec() / 1000.0

		_path_index = 0
		# A new path starts clean from the current rendered position, no leftover correction.
		_correction_offset = Vector3.ZERO
		_correcting = false
		_start_movement_to_next_waypoint(current_time, position)
	else:
		# No path, stop movement
		_is_moving = false


func update_speed(msg: SpeedComponentSMSG) -> void:
	var new_speed = msg.Speed

	# If speed changed while moving, adjust the movement timing
	if _is_moving and new_speed != _speed and new_speed > 0.0:
		var current_time = Time.get_ticks_msec() / 1000.0

		# Calculate how far we've moved with the old speed
		var elapsed_time = current_time - _movement_start_time
		var distance_covered = elapsed_time * _speed if _speed > 0.0 else 0.0
		var total_distance = _movement_start_position.distance_to(_target_position)

		if distance_covered < total_distance:
			# Recalculate timing with new speed
			var progress = distance_covered / total_distance if total_distance > 0.0 else 1.0
			var current_pos = _movement_start_position.lerp(_target_position, progress)

			# Update movement parameters
			_movement_start_position = current_pos
			_movement_start_time = current_time
			position = current_pos + _correction_offset

	_speed = new_speed

	# Stop movement if speed becomes 0
	if _speed <= 0.0:
		_is_moving = false


func show_damage(msg: DamageEntitySMSG) -> void:
	var visual = _get_visual_for_method("show_damage")
	if visual != null:
		visual.show_damage(msg)


func update_health(msg: HealthComponentSMSG) -> void:
	var visual = _get_visual_for_method("update_health")
	if visual != null:
		visual.update_health(msg)


func select_for_active() -> void:
	_camera = Camera.instantiate()
	add_child(_camera)


func _get_visual_for_method(method_name: String) -> Visual:
	var visual = get_node_or_null(_VISUAL_NODE_NAME)
	if visual != null && visual.has_method(method_name):
		return visual
	else:
		printerr("Entity %s visual method %s handler missing, dropping message" % [entity_id, method_name])
		return null


func vanish(msg: VanishEntitySMSG) -> void:
	# Check if a visual node can handle the vanish.
	var visual = get_node_or_null(_VISUAL_NODE_NAME)
	if visual != null && visual.has_method("vanish"):
		visual.vanish(msg)
	else:
		print("Entity: Entity %s no vanish handler, simply remove it" % [entity_id])
		queue_free()


func remove_as_active() -> void:
	if _camera != null:
		_camera.queue_free()
		_camera = null


func _ease_out_cubic(t: float) -> float:
	# Cubic ease-out function for smooth corrections
	var f = t - 1.0
	return f * f * f + 1.0
