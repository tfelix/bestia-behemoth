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

var MasterModelScn = preload("res://Game/Entity/Master/Master.tscn")
var Camera = preload("res://Game/SpringArmCamera/SpringArmCamera.tscn")
var ChatText = preload("res://Game/Entity/ChatText/ChatText.tscn")

var entity_id: int = 0

var _camera: Node3D = null
var _speed: float = 1.0

@onready var _chat_anchor: Node3D = $ChatAnchor

# Movement prediction system
var _current_path: Array[Vector3] = []
var _path_index: int = 0
var _movement_start_time: float = 0.0
var _movement_start_position: Vector3 = Vector3.ZERO
var _target_position: Vector3 = Vector3.ZERO
var _is_moving: bool = false
var _server_position_correction: bool = false
var _correction_start_position: Vector3 = Vector3.ZERO
var _correction_target_position: Vector3 = Vector3.ZERO
var _correction_start_time: float = 0.0
const _CORRECTION_DURATION: float = 0.2  # Quick correction to server position
const _POSITION_THRESHOLD: float = 0.05  # Threshold for server position corrections

const _VISUAL_NODE_NAME = "Visual"
const _CHAT_NODE_NAME = "ChatText"


func _process(delta: float) -> void:
	_update_movement(delta)


func _update_movement(_delta: float) -> void:
	var current_time = Time.get_ticks_msec() / 1000.0  # More reliable time source

	# Handle server position correction first (higher priority)
	if _server_position_correction:
		var correction_progress = (current_time - _correction_start_time) / _CORRECTION_DURATION
		if correction_progress >= 1.0:
			# Correction complete
			position = _correction_target_position
			_server_position_correction = false
			# Resume normal movement if we have a path
			if _current_path.size() > 0 and _path_index < _current_path.size():
				_resume_path_movement(current_time)
		else:
			# Smooth lerp to server position
			var eased_progress = _ease_out_cubic(correction_progress)
			position = _correction_start_position.lerp(_correction_target_position, eased_progress)
		return

	# Handle normal path movement
	if _is_moving and _current_path.size() > _path_index and _speed > 0.0:
		var movement_progress = (current_time - _movement_start_time) * _speed
		var distance_to_target = _movement_start_position.distance_to(_target_position)

		if movement_progress >= distance_to_target:
			# Reached current waypoint
			position = _target_position
			_path_index += 1

			# Check if there are more waypoints
			if _path_index < _current_path.size():
				_start_movement_to_next_waypoint(current_time)
			else:
				# Path complete
				_is_moving = false
		else:
			# Move towards target
			var t = movement_progress / distance_to_target if distance_to_target > 0 else 1.0
			position = _movement_start_position.lerp(_target_position, t)


func _start_movement_to_next_waypoint(current_time: float) -> void:
	if _path_index >= _current_path.size():
		_is_moving = false
		return

	_movement_start_position = position
	_target_position = _current_path[_path_index]
	_movement_start_time = current_time
	_is_moving = true


func _resume_path_movement(current_time: float) -> void:
	if _path_index < _current_path.size():
		_movement_start_position = position
		_target_position = _current_path[_path_index]
		_movement_start_time = current_time
		_is_moving = true


# This does not work out. we add the visual which contains the model only later
# so we either hand over the chat message to the visual to handle it (what happens
# if visual not yet loaded?).
func show_chat(msg: ChatSMSG) -> void:
	for x in _chat_anchor.get_children():
		x.queue_free()
	var chat_text = ChatText.instantiate()
	chat_text.chat_msg = msg
	chat_text.name = _CHAT_NODE_NAME
	_chat_anchor.add_child(chat_text)


func update_bestia_visual(msg: BestiaVisualComponent) -> void:
	var existing = get_node_or_null(_VISUAL_NODE_NAME)
	if existing != null:
		existing.queue_free()
	var visual = BestiaModelScn.instantiate() as BestiaVisual
	visual.setup_visual(msg)
	visual.name = _VISUAL_NODE_NAME
	add_child(visual)


func update_master_visual(msg: MasterVisualComponentSMSG) -> void:
	var existing = get_node_or_null(_VISUAL_NODE_NAME)
	if existing != null:
		existing.queue_free()
	var visual = MasterModelScn.instantiate() as Master
	visual.setup_visual(msg)
	visual.name = _VISUAL_NODE_NAME
	add_child(visual)


func update_position(msg: PositionComponent) -> void:
	var new_position = msg.Position

	# If we're currently moving and the server position is different from our prediction,
	# start a correction lerp
	if _is_moving and position.distance_to(new_position) > _POSITION_THRESHOLD:
		var current_time = Time.get_ticks_msec() / 1000.0

		_server_position_correction = true
		_correction_start_position = position
		_correction_target_position = new_position
		_correction_start_time = current_time
	else:
		# Direct position update (entity not moving or positions match)
		position = new_position
		# If we were doing a correction, cancel it
		if _server_position_correction:
			_server_position_correction = false


func update_path(msg: PathComponentSMSG) -> void:
	# Convert C# path to Godot Vector3 array
	_current_path.clear()
	for vec3 in msg.Path:
		_current_path.append(vec3)

	# Start movement if we have waypoints
	if _current_path.size() > 0:
		var current_time = Time.get_ticks_msec() / 1000.0

		_path_index = 0
		# Cancel any ongoing correction as we have a new path
		_server_position_correction = false
		_start_movement_to_next_waypoint(current_time)
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
			position = current_pos

	_speed = new_speed

	# Stop movement if speed becomes 0
	if _speed <= 0.0:
		_is_moving = false


func show_damage() -> void:
	pass


func select_for_active() -> void:
	_camera = Camera.instantiate()
	add_child(_camera)


func vanish(msg: VanishEntitySMSG) -> void:
	# Check if a visual node can handle the vanish.
	var visual = get_node_or_null(_VISUAL_NODE_NAME)
	if visual != null && visual.has_method("vanish"):
		visual.vanish(msg)
	else:
		print("Entity %s vanish not handled, removing it" % [entity_id])
		queue_free()


func remove_as_active() -> void:
	if _camera != null:
		_camera.queue_free()
		_camera = null


func _ease_out_cubic(t: float) -> float:
	# Cubic ease-out function for smooth corrections
	var f = t - 1.0
	return f * f * f + 1.0
