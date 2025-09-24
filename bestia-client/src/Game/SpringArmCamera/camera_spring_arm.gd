extends Node3D

# Inspired by https://www.youtube.com/watch?v=ZCb12AHKMfE

@export var mouse_sensibility :=  0.0035
@export_range(-90.0, 0.0, 0.1, "radians_as_degrees") var min_vertical_angle: float = -PI/2
@export_range(0.0, 90.0, 0.1, "radians_as_degrees") var max_vertical_angle: float = PI/4
@export_range(1, 15, 1, "min_camera_distance_as_meter") var min_cam_distance: int = 3
@export_range(15, 30, 1, "max_camera_distance_as_meter") var max_cam_distance: int = 10


@onready var spring_arm := $SpringArm3D


func _unhandled_input(event: InputEvent) -> void:
	if event is InputEventMouseMotion and Input.mouse_mode == Input.MOUSE_MODE_CAPTURED:
		rotation.y -= event.relative.x * mouse_sensibility
		rotation.y = wrapf(rotation.y, 0.0, TAU) # between 0 and 360 degree.
		rotation.x -= event.relative.y * mouse_sensibility
		rotation.x = clamp(rotation.x, min_vertical_angle, max_vertical_angle)
	
	if event.is_action_pressed("camera_zoom_in"):
		spring_arm.spring_length -= 1
		spring_arm.spring_length = clamp(spring_arm.spring_length, min_cam_distance, max_cam_distance)
	if event.is_action_pressed("camera_zoom_out"):
		spring_arm.spring_length += 1
		spring_arm.spring_length = clamp(spring_arm.spring_length, min_cam_distance, max_cam_distance)
	
	if event.is_action_released("camera_mouse_capture"):
		if Input.mouse_mode == Input.MOUSE_MODE_CAPTURED:
			Input.set_mouse_mode(Input.MOUSE_MODE_VISIBLE)

	if event.is_action_pressed("camera_mouse_capture"):
		Input.set_mouse_mode(Input.MOUSE_MODE_CAPTURED)
