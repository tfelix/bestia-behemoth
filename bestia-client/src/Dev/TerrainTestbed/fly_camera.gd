extends Camera3D

## Free flight for the testbed scene, so the field can be walked around when it is run rather than edited.
##
## Deliberately not a [code]@tool[/code] script: in the editor the viewport already flies, and a camera that
## drove itself there would fight whoever was navigating. It does nothing until the scene is actually running.
##
## Hold the right mouse button to look, [b]WASD[/b] to move, [b]Q[/b]/[b]E[/b] for down and up, [b]Shift[/b]
## to go four times faster.

## Metres per second at a walk.
@export_range(1.0, 100.0, 0.5) var speed: float = 14.0

## What Shift multiplies that by.
@export_range(1.0, 10.0, 0.5) var sprint: float = 4.0

## Radians of rotation per pixel of mouse travel.
@export_range(0.0005, 0.02, 0.0005) var sensitivity: float = 0.004

var _yaw: float = 0.0
var _pitch: float = 0.0
var _looking: bool = false


func _ready() -> void:
	# Seeded from whatever the scene was authored with, so the first right-click does not snap the view.
	_yaw = rotation.y
	_pitch = rotation.x


func _unhandled_input(event: InputEvent) -> void:
	if event is InputEventMouseButton and event.button_index == MOUSE_BUTTON_RIGHT:
		_looking = event.pressed
		Input.mouse_mode = Input.MOUSE_MODE_CAPTURED if _looking else Input.MOUSE_MODE_VISIBLE
		return

	if event is InputEventMouseMotion and _looking:
		_yaw -= event.relative.x * sensitivity

		# Stopped just short of straight up and straight down: at exactly vertical the yaw axis and the view
		# direction line up and the camera rolls instead of turning.
		_pitch = clampf(_pitch - event.relative.y * sensitivity, -1.5, 1.5)

		rotation = Vector3(_pitch, _yaw, 0.0)


func _process(delta: float) -> void:
	var move := Vector3(
		Input.get_axis(&"ui_left", &"ui_right"),
		0.0,
		Input.get_axis(&"ui_up", &"ui_down"))

	# The arrow keys come free with ui_*; WASD and the vertical pair are read raw, because binding six actions
	# in project.godot for one dev scene would put testbed input in front of every player of the game.
	move.x += float(Input.is_key_pressed(KEY_D)) - float(Input.is_key_pressed(KEY_A))
	move.z += float(Input.is_key_pressed(KEY_S)) - float(Input.is_key_pressed(KEY_W))
	move.y += float(Input.is_key_pressed(KEY_E)) - float(Input.is_key_pressed(KEY_Q))

	if move == Vector3.ZERO:
		return

	var pace := speed * (sprint if Input.is_key_pressed(KEY_SHIFT) else 1.0)

	# Horizontal movement follows where the camera is pointed; up and down stay world-aligned, so looking at
	# your feet and pressing E still rises rather than flying backwards.
	translate_object_local(Vector3(move.x, 0.0, move.z).normalized() * pace * delta)
	global_position.y += move.y * pace * delta
