extends TextureRect

## Room left around a bestia's body, as a factor of the tightest fit.
const _BESTIA_FRAME_MARGIN := 1.25

@onready var _master_visual: MasterVisual = %MasterVisual
@onready var _viewport: SubViewport = %PortraitViewport
@onready var _camera: Camera3D = $PortraitViewport/PortraitScene/Camera3D

## Framed by hand in the scene for a master; a bestia is framed from its own bounds instead.
@onready var _master_camera_transform: Transform3D = _camera.transform

var _bestia_visual: Node3D = null


func apply_visual(msg: MasterVisualComponentSMSG) -> void:
	_master_visual.setup_visual(msg)
	if _bestia_visual == null:
		_render_once()


func show_master() -> void:
	_drop_bestia_visual()
	_master_visual.visible = true
	_camera.transform = _master_camera_transform
	_render_once()


func show_bestia(mob_id: int) -> void:
	_drop_bestia_visual()
	_master_visual.visible = false

	var species := BestiaDB.get_instance().get_bestia(mob_id)
	var scene := species.get_bestia_visual() if species != null else BestiaResource.MISSING_VISUAL
	_bestia_visual = scene.instantiate()
	if _bestia_visual.has_method("apply_placeholder"):
		_bestia_visual.apply_placeholder(species)
	_master_visual.get_parent().add_child(_bestia_visual)
	_finish_appear_animation(_bestia_visual)
	_frame(VisualBounds.of_meshes(_bestia_visual))
	_render_once()


func _drop_bestia_visual() -> void:
	if _bestia_visual == null:
		return
	_bestia_visual.queue_free()
	_bestia_visual = null


## A visual fades itself in when it enters the tree; the portrait is rendered once, so it must not catch
## that fade halfway.
func _finish_appear_animation(visual: Node3D) -> void:
	var player := visual.get_node_or_null("AnimationPlayer") as AnimationPlayer
	if player != null and player.is_playing():
		player.advance(player.current_animation_length)


## Backs the camera off along its view axis until the whole body fits both the height and the width.
func _frame(bounds: AABB) -> void:
	if not bounds.has_volume():
		_camera.transform = _master_camera_transform
		return

	var half_vertical_fov := deg_to_rad(_camera.fov) * 0.5
	var aspect := float(_viewport.size.x) / float(_viewport.size.y)
	var half_horizontal_fov := atan(tan(half_vertical_fov) * aspect)

	var half_size := bounds.size * 0.5
	var distance := maxf(half_size.y / tan(half_vertical_fov), half_size.x / tan(half_horizontal_fov))
	distance = distance * _BESTIA_FRAME_MARGIN + half_size.z

	var centre := bounds.get_center()
	_camera.transform = Transform3D(Basis.IDENTITY, centre + Vector3(0, 0, distance))


func _render_once() -> void:
	# A frame later, so the model change has reached the render server.
	await get_tree().process_frame
	_viewport.render_target_update_mode = SubViewport.UPDATE_ONCE
