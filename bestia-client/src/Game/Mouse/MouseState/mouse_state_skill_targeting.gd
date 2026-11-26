extends MouseState
class_name MouseStateSkillTargeting

## Active while casting a skill. Tracks the mouse over the floor every frame
## (physics-picking signals only fire on discrete events, not continuously, so this
## needs its own per-frame raycast - see MouseManager.get_floor_hit_at_mouse) and
## branches on skill.target_type:
## - GROUND / AOE_GROUND: a ground-position marker (AOE_GROUND additionally sized to
##   skill.aoe_radius) that confirms at the raycast hit point.
## - ENEMY / FRIENDLY: snaps onto the closest matching entity within
##   SettingsManager.skill_target_snap_distance of the mouse's floor position (Shift
##   inverts the enemy/friendly filter) and only confirms while snapped. A skill that
##   targets enchanted items, like Dispel, also snaps onto a sealed item on the ground.
##
## The mouse wheel picks the level of this one cast, between 1 and [member max_skill_level], and a small tag beside
## the marker shows it. The slot the skill came from keeps its own level for the next cast.

const _DEFAULT_GROUND_INDICATOR_SCENE := preload("res://Game/VFX/AOECastIndicator/AOECastIndicator.tscn")
const _ENTITY_SNAP_INDICATOR_SCENE := preload("res://Game/VFX/EntitySnapIndicator/EntitySnapIndicator.tscn")

## How far the level tag sits off an entity's feet: right of it and lifted to about its waist.
const _ENTITY_TAG_OFFSET := Vector3(0.6, 0.6, 0.6)
const _TAG_LIFT := 0.3
const _TAG_COLOR := Color(0.98, 0.78, 0.46)

var skill: AttackResource
var skill_level: int = 1
## The highest level the wheel may pick: the learned one. Equal to [member skill_level] when the level is fixed, such
## as a spell scroll's, which leaves the wheel to the camera (see MouseManager.is_choosing_skill_level).
var max_skill_level: int = 1
## The choice a picker made before targeting began, sent along with the target; empty for most skills.
var args: PackedInt64Array = PackedInt64Array()
var indicator_scene: PackedScene = null
## Called with the target position and entity id (0 for ground) instead of casting, for something aimed like a
## skill that is not one, such as reading a spell scroll. Casts the skill when unset.
var on_confirm := Callable()

var _indicator: Node3D = null
var _level_tag: Label3D = null
var _last_ground_hit = null
var _snap_target: Entity = null


func _is_entity_target() -> bool:
	return skill.target_type == AttackResource.TARGET_TYPE_ENEMY or skill.target_type == AttackResource.TARGET_TYPE_FRIENDLY


func enter(mgr: MouseManager) -> void:
	# Aiming is done with the 3D indicator below, so the pointer stays the plain one rather than keeping
	# whatever the target under it asked for before the cast began.
	mgr.set_cursor_for_action(DefaultAction.Kind.NONE)

	if _is_entity_target():
		_indicator = _ENTITY_SNAP_INDICATOR_SCENE.instantiate()
	else:
		_indicator = (indicator_scene if indicator_scene else _DEFAULT_GROUND_INDICATOR_SCENE).instantiate()
		if _indicator.has_method("set_radius"):
			_indicator.set_radius(_aoe_radius())
	mgr.add_child(_indicator)
	_indicator.visible = false

	_level_tag = Label3D.new()
	_level_tag.billboard = BaseMaterial3D.BILLBOARD_ENABLED
	_level_tag.no_depth_test = true
	_level_tag.fixed_size = true
	_level_tag.pixel_size = 0.0014
	_level_tag.font_size = 18
	_level_tag.outline_size = 6
	_level_tag.modulate = _TAG_COLOR
	_level_tag.outline_modulate = Color(0.1, 0.08, 0.2, 0.85)
	_level_tag.horizontal_alignment = HORIZONTAL_ALIGNMENT_LEFT
	_level_tag.visible = false
	mgr.add_child(_level_tag)
	_show_level()


func exit(mgr: MouseManager) -> void:
	if _indicator:
		_indicator.queue_free()
		_indicator = null
	if _level_tag:
		_level_tag.queue_free()
		_level_tag = null
	_snap_target = null


func handle_input(mgr: MouseManager, event: InputEvent) -> void:
	if max_skill_level <= 1 or mgr.is_pointer_over_ui():
		return
	# The zoom actions rather than the raw buttons, as placement turns its ghost with them; camera_spring_arm.gd
	# skips its zoom meanwhile, see MouseManager.is_choosing_skill_level. Wheel up raises the level.
	var step := 0
	if event.is_action_pressed("camera_zoom_out"):
		step = 1
	elif event.is_action_pressed("camera_zoom_in"):
		step = -1
	if step != 0:
		skill_level = clampi(skill_level + step, 1, max_skill_level)
		_show_level()


func _show_level() -> void:
	if _level_tag:
		_level_tag.text = TranslationServer.translate("SKILL_TARGET_LEVEL") % skill_level


## Tiles the cast reaches from its centre: none past it for a plain ground skill.
func _aoe_radius() -> float:
	return skill.aoe_radius if skill.target_type == AttackResource.TARGET_TYPE_AOE_GROUND else 0.0


func process_state(mgr: MouseManager, delta: float) -> void:
	_last_ground_hit = mgr.get_floor_hit_at_mouse()
	if _is_entity_target():
		_update_entity_snap(mgr)
	else:
		_update_ground_indicator()


func _update_ground_indicator() -> void:
	if _indicator == null:
		return
	if _last_ground_hit == null:
		_indicator.visible = false
		_place_tag(null)
	else:
		_indicator.visible = true
		# Snapped to the tile that will actually be hit rather than sliding with the mouse. The
		# footprint is an odd number of tiles centred on the target one (see AOECastIndicator.set_radius),
		# so centring the decal on that tile's centre makes it cover exactly the cells that will burn -
		# drawn at the raw hit it was up to half a tile out from them on each axis.
		var centre := TileSpace.tile_centre(TileSpace.world_to_tile(_last_ground_hit))
		_indicator.global_position = Vector3(centre.x, _last_ground_hit.y, centre.z)
		# Just outside the footprint's corner, so the number never covers a cell that will be hit.
		var corner := _aoe_radius() + 0.5
		_place_tag(_indicator.global_position + Vector3(corner, _TAG_LIFT, corner))


func _place_tag(at) -> void:
	if _level_tag == null:
		return
	_level_tag.visible = at != null
	if at != null:
		_level_tag.global_position = at


func _update_entity_snap(mgr: MouseManager) -> void:
	_snap_target = null
	if _indicator:
		_indicator.visible = false
	_place_tag(null)
	if _last_ground_hit == null:
		return

	var shift_held := Input.is_key_pressed(KEY_SHIFT)
	var base_filter := "enemy" if skill.target_type == AttackResource.TARGET_TYPE_ENEMY else "friendly"
	var effective_filter := base_filter
	if shift_held:
		effective_filter = "friendly" if base_filter == "enemy" else "enemy"
	if effective_filter == "enemy" and skill.targets_enchanted_items:
		effective_filter = "enemy_or_enchanted_item"
	if skill.targets_fallen:
		effective_filter = "fallen"

	var entity_manager := EntityManager.get_instance()
	if entity_manager == null:
		return

	# TODO this is a very inefficient way to do this. It would be far better if every entity just has some sort of "influence sphere" and then you
	# just select this entity. The clickable collision box could be used.
	_snap_target = entity_manager.get_closest_entity(_last_ground_hit, SettingsManager.get_instance().skill_target_snap_distance, effective_filter)
	if _snap_target and _indicator:
		_indicator.visible = true
		_indicator.global_position = _snap_target.global_position
		_place_tag(_snap_target.global_position + _ENTITY_TAG_OFFSET)


func handle_object_clicked(mgr: MouseManager, object: Node3D, event: InputEvent, click_position: Vector3) -> void:
	_try_confirm(mgr, event)


func handle_ground_input_event(mgr: MouseManager, click_position: Vector3, event: InputEvent) -> void:
	_try_confirm(mgr, event)


func handle_cancel(mgr: MouseManager) -> void:
	mgr.enter_default()


func _try_confirm(mgr: MouseManager, event: InputEvent) -> void:
	if not event.is_action_pressed("normal_action"):
		return
	if _is_entity_target():
		if _snap_target == null:
			return
		# The logical position rather than the drawn one, for the reason ConnectionManager.move_to uses
		# it: this is rounded to a whole tile on the wire, and the ground correction on `position` can
		# round it to the neighbouring voxel. The indicator above deliberately uses the drawn one -
		# that is a mark on the model, not a coordinate for the server.
		_confirm(_snap_target.get_logical_position(), _snap_target.entity_id)
	else:
		if _last_ground_hit == null:
			return
		# The tile under the cursor, not the raw hit: Vec3Convert rounds whatever it is handed, and a
		# world position rounds to the tile whose *centre* is nearest, which is the neighbour of the one
		# the indicator just drew for half of every tile. See TileSpace.
		_confirm(TileSpace.world_to_tile(_last_ground_hit), 0)
	mgr.enter_default()


func _confirm(target_position: Vector3, target_entity_id: int) -> void:
	if on_confirm.is_valid():
		on_confirm.call(target_position, target_entity_id)
	else:
		ConnectionManager.activate_skill(skill.skill_id, skill_level, target_position, target_entity_id, args)
