extends Node3D

# Inspired by https://www.youtube.com/watch?v=ZCb12AHKMfE

@export var mouse_sensibility :=  0.0035
@export_range(-90.0, 0.0, 0.1, "radians_as_degrees") var min_vertical_angle: float = -PI/2
@export_range(-40, 0.0, 0.1, "radians_as_degrees") var max_vertical_angle: float = PI/4
@export_range(1, 15, 1, "min_camera_distance_as_meter") var min_cam_distance: int = 3
@export_range(15, 50, 1, "max_camera_distance_as_meter") var max_cam_distance: int = 36


@onready var spring_arm := $SpringArm3D

const CAMERA_SAVE_PATH = "user://camera_config.json"

const _RMB_DRAG_THRESHOLD_PX: float = 5.0

var _rmb_pressed: bool = false
var _rmb_dragged: bool = false
var _rmb_press_position: Vector2 = Vector2.ZERO


func _ready() -> void:
	load_camera()


## Runs when the game scene is left as well as on shutdown, since the engine frees
## the scene tree on quit.
func _exit_tree() -> void:
	save_camera()


func _unhandled_input(event: InputEvent) -> void:
	if event is InputEventMouseMotion and Input.mouse_mode == Input.MOUSE_MODE_CAPTURED:
		rotation.y -= event.relative.x * mouse_sensibility
		rotation.y = wrapf(rotation.y, 0.0, TAU) # between 0 and 360 degree.
		rotation.x -= event.relative.y * mouse_sensibility
		rotation.x = clamp(rotation.x, min_vertical_angle, max_vertical_angle)

	# RMB doesn't capture the mouse immediately: a "clean" press+release (no
	# drag past the threshold) is a right-click for MouseManager's context
	# menu, only a drag beyond the threshold starts the camera-look capture.
	if event is InputEventMouseMotion and _rmb_pressed and not _rmb_dragged:
		if event.position.distance_to(_rmb_press_position) > _RMB_DRAG_THRESHOLD_PX:
			_rmb_dragged = true
			Input.set_mouse_mode(Input.MOUSE_MODE_CAPTURED)

	if event.is_action_pressed("camera_zoom_in"):
		spring_arm.spring_length -= 1
		spring_arm.spring_length = clamp(spring_arm.spring_length, min_cam_distance, max_cam_distance)
	if event.is_action_pressed("camera_zoom_out"):
		spring_arm.spring_length += 1
		spring_arm.spring_length = clamp(spring_arm.spring_length, min_cam_distance, max_cam_distance)

	if event.is_action_pressed("camera_mouse_capture"):
		if MouseManager.is_targeting():
			MouseManager.cancel_targeting()
			return
		_rmb_pressed = true
		_rmb_dragged = false
		_rmb_press_position = get_viewport().get_mouse_position()

	if event.is_action_released("camera_mouse_capture") and _rmb_pressed:
		_rmb_pressed = false
		if _rmb_dragged:
			_rmb_dragged = false
			if Input.mouse_mode == Input.MOUSE_MODE_CAPTURED:
				Input.set_mouse_mode(Input.MOUSE_MODE_VISIBLE)
		else:
			MouseManager.right_clicked(get_viewport().get_mouse_position())


func save_camera() -> void:
	var config = {
		"yaw": rotation.y,
		"pitch": rotation.x,
		"spring_length": spring_arm.spring_length,
	}

	var file = FileAccess.open(CAMERA_SAVE_PATH, FileAccess.WRITE)
	if not file:
		push_error("Failed to save camera configuration")
		return

	file.store_string(JSON.stringify(config, "\t"))
	file.close()


func load_camera() -> void:
	if not FileAccess.file_exists(CAMERA_SAVE_PATH):
		print("No camera configuration found, using scene defaults")
		return

	var file = FileAccess.open(CAMERA_SAVE_PATH, FileAccess.READ)
	if not file:
		push_error("Failed to load camera configuration")
		return

	var json_string = file.get_as_text()
	file.close()

	var json = JSON.new()
	var error = json.parse(json_string)
	if error != OK:
		push_error("Failed to parse camera JSON: ", json.get_error_message())
		return

	var config = json.data
	if typeof(config) != TYPE_DICTIONARY:
		push_error("Invalid camera data format")
		return

	# Re-apply the same wrap/clamp as the input handling: the saved values might come
	# from a build with different angle or distance limits.
	rotation.y = wrapf(_read_float(config, "yaw", rotation.y), 0.0, TAU)
	rotation.x = clamp(_read_float(config, "pitch", rotation.x), min_vertical_angle, max_vertical_angle)
	spring_arm.spring_length = clamp(
		_read_float(config, "spring_length", spring_arm.spring_length),
		min_cam_distance,
		max_cam_distance
	)


func _read_float(config: Dictionary, key: String, fallback: float) -> float:
	var value = config.get(key, fallback)
	if typeof(value) != TYPE_FLOAT and typeof(value) != TYPE_INT:
		return fallback
	return float(value)
