class_name CompendiumModelView
extends SubViewportContainer

## A species' 3D model on neutral ground, seen at an angle from above. It turns slowly by itself, and
## dragging turns it by hand. Renders only while visible, so a closed compendium costs nothing.

const _SIZE := Vector2(150, 162)
const _ELEVATION_DEGREES := 35.0
const _FOV_DEGREES := 40.0
## Room left around the model, as a factor of its bounding sphere.
const _FRAME_MARGIN := 1.35
const _TURN_DEGREES_PER_SECOND := 18.0
const _DRAG_DEGREES_PER_PIXEL := 0.6
## How long a drag holds the turntable still after the mouse is let go.
const _RESUME_AFTER_SECONDS := 2.0
const _GROUND := Color(0.62, 0.60, 0.56)
const _SKY := Color(0.20, 0.21, 0.24)
## Nodes of an entity visual that belong to the game world, not to a picture of the species.
const _WORLD_ONLY_NODES := ["NameTag", "HealthBar", "CastBar", "DamageTagAnchor"]

var _viewport: SubViewport
var _turntable: Node3D
var _camera: Camera3D
var _model: Node3D
var _species_id := 0
var _dragging := false
var _idle_for := _RESUME_AFTER_SECONDS


func _init() -> void:
	stretch = true
	custom_minimum_size = _SIZE
	mouse_default_cursor_shape = Control.CURSOR_DRAG

	_viewport = SubViewport.new()
	_viewport.own_world_3d = true
	_viewport.msaa_3d = Viewport.MSAA_4X
	_viewport.render_target_update_mode = SubViewport.UPDATE_WHEN_VISIBLE
	add_child(_viewport)

	var scene := Node3D.new()
	_viewport.add_child(scene)
	scene.add_child(_environment())
	scene.add_child(_key_light())
	scene.add_child(_ground())

	_turntable = Node3D.new()
	scene.add_child(_turntable)

	_camera = Camera3D.new()
	_camera.fov = _FOV_DEGREES
	scene.add_child(_camera)


func shown_species_id() -> int:
	return _species_id


func show_species(species: BestiaResource) -> void:
	clear()
	_species_id = species.bestia_id
	_model = species.get_bestia_visual().instantiate()
	if _model.has_method("apply_placeholder"):
		_model.apply_placeholder(species)
	for world_only in _WORLD_ONLY_NODES:
		var node := _model.get_node_or_null(world_only)
		if node != null:
			node.queue_free()
	_turntable.add_child(_model)
	_finish_appear_animation(_model)
	_turntable.rotation = Vector3.ZERO
	_frame(VisualBounds.of_meshes(_model))
	visible = true


func clear() -> void:
	visible = false
	_species_id = 0
	if _model != null:
		_model.queue_free()
		_model = null


func _process(delta: float) -> void:
	if _model == null or _dragging:
		return
	_idle_for += delta
	if _idle_for >= _RESUME_AFTER_SECONDS:
		_turntable.rotate_y(deg_to_rad(_TURN_DEGREES_PER_SECOND) * delta)


func _gui_input(event: InputEvent) -> void:
	var button := event as InputEventMouseButton
	if button != null and button.button_index == MOUSE_BUTTON_LEFT:
		_dragging = button.pressed
		_idle_for = 0.0
		accept_event()
		return

	var motion := event as InputEventMouseMotion
	if motion != null and _dragging:
		_turntable.rotate_y(deg_to_rad(motion.relative.x * _DRAG_DEGREES_PER_PIXEL))
		accept_event()


## Backs the camera off along a fixed downward angle until the model's bounding sphere fits.
func _frame(bounds: AABB) -> void:
	var centre := bounds.get_center() if bounds.has_volume() else Vector3(0, 0.5, 0)
	var radius := bounds.size.length() * 0.5 if bounds.has_volume() else 1.0
	var distance := radius * _FRAME_MARGIN / sin(deg_to_rad(_FOV_DEGREES) * 0.5)

	var elevation := deg_to_rad(_ELEVATION_DEGREES)
	var offset := Vector3(0, sin(elevation), cos(elevation)) * distance
	_camera.position = centre + offset
	_camera.look_at(centre, Vector3.UP)


## A visual fades itself in when it enters the tree; the picture should show it whole at once.
func _finish_appear_animation(visual: Node3D) -> void:
	var player := visual.get_node_or_null("AnimationPlayer") as AnimationPlayer
	if player != null and player.is_playing():
		player.advance(player.current_animation_length)


func _environment() -> WorldEnvironment:
	var environment := Environment.new()
	environment.background_mode = Environment.BG_COLOR
	environment.background_color = _SKY
	environment.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	environment.ambient_light_color = Color(0.85, 0.85, 0.9)
	environment.ambient_light_energy = 0.55
	var world := WorldEnvironment.new()
	world.environment = environment
	return world


func _key_light() -> DirectionalLight3D:
	var light := DirectionalLight3D.new()
	light.rotation_degrees = Vector3(-50, 35, 0)
	light.light_energy = 1.1
	light.shadow_enabled = true
	return light


func _ground() -> MeshInstance3D:
	var disc := CylinderMesh.new()
	disc.top_radius = 12.0
	disc.bottom_radius = 12.0
	disc.height = 0.02
	var material := StandardMaterial3D.new()
	material.albedo_color = _GROUND
	material.roughness = 1.0
	disc.material = material
	var ground := MeshInstance3D.new()
	ground.mesh = disc
	ground.position = Vector3(0, -0.01, 0)
	return ground
