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
##   inverts the enemy/friendly filter) and only confirms while snapped.

const _DEFAULT_GROUND_INDICATOR_SCENE := preload("res://Game/VFX/AOECastIndicator/AOECastIndicator.tscn")
const _ENTITY_SNAP_INDICATOR_SCENE := preload("res://Game/VFX/EntitySnapIndicator/EntitySnapIndicator.tscn")

var skill: AttackResource
var skill_level: int = 1
var indicator_scene: PackedScene = null

var _indicator: Node3D = null
var _last_ground_hit = null
var _snap_target: Entity = null


func _is_entity_target() -> bool:
	return skill.target_type == AttackResource.TARGET_TYPE_ENEMY or skill.target_type == AttackResource.TARGET_TYPE_FRIENDLY


func enter(mgr) -> void:
	if _is_entity_target():
		_indicator = _ENTITY_SNAP_INDICATOR_SCENE.instantiate()
	else:
		_indicator = (indicator_scene if indicator_scene else _DEFAULT_GROUND_INDICATOR_SCENE).instantiate()
		if skill.target_type == AttackResource.TARGET_TYPE_AOE_GROUND and _indicator.has_method("set_radius"):
			_indicator.set_radius(skill.aoe_radius)
	mgr.add_child(_indicator)
	_indicator.visible = false


func exit(mgr) -> void:
	if _indicator:
		_indicator.queue_free()
		_indicator = null
	_snap_target = null


func process_state(mgr, delta: float) -> void:
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
	else:
		_indicator.visible = true
		_indicator.global_position = _last_ground_hit


func _update_entity_snap(mgr) -> void:
	_snap_target = null
	if _indicator:
		_indicator.visible = false
	if _last_ground_hit == null:
		return

	var shift_held := Input.is_key_pressed(KEY_SHIFT)
	var base_filter := "enemy" if skill.target_type == AttackResource.TARGET_TYPE_ENEMY else "friendly"
	var effective_filter := base_filter
	if shift_held:
		effective_filter = "friendly" if base_filter == "enemy" else "enemy"

	var entity_manager = mgr.get_tree().get_first_node_in_group("entity_manager")
	if entity_manager == null:
		return

	# TODO this is a very inefficient way to do this. It would be far better if every entity just has some sort of "influence sphere" and then you
	# just select this entity. The clickable collision box could be used.
	_snap_target = entity_manager.get_closest_entity(_last_ground_hit, SettingsManager.skill_target_snap_distance, effective_filter)
	if _snap_target and _indicator:
		_indicator.visible = true
		_indicator.global_position = _snap_target.global_position


func handle_object_clicked(mgr, object: Node3D, event: InputEvent, click_position: Vector3) -> void:
	_try_confirm(mgr, event)


func handle_ground_input_event(mgr, click_position: Vector3, event: InputEvent) -> void:
	_try_confirm(mgr, event)


func handle_cancel(mgr) -> void:
	mgr.enter_default()


func _try_confirm(mgr, event: InputEvent) -> void:
	if not event.is_action_pressed("normal_action"):
		return
	if _is_entity_target():
		if _snap_target == null:
			return
		ConnectionManager.activate_skill(skill.skill_id, skill_level, _snap_target.global_position, _snap_target.entity_id)
	else:
		if _last_ground_hit == null:
			return
		ConnectionManager.activate_skill(skill.skill_id, skill_level, _last_ground_hit)
	mgr.enter_default()
