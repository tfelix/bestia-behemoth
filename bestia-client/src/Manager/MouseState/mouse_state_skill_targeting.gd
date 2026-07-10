extends MouseState
class_name MouseStateSkillTargeting

## Active while casting a ground-targeted skill. Tracks the mouse over the
## floor every frame (physics-picking signals only fire on discrete events,
## not continuously, so this needs its own per-frame raycast - see
## MouseManager.get_floor_hit_at_mouse) and confirms on the next click.

const _DEFAULT_INDICATOR_SCENE := preload("res://Game/VFX/AOECastIndicator/AOECastIndicator.tscn")

var skill: AttackResource
var skill_level: int = 1
var indicator_scene: PackedScene = _DEFAULT_INDICATOR_SCENE

var _indicator: Node3D = null
var _last_ground_hit = null


func enter(mgr) -> void:
	_indicator = indicator_scene.instantiate()
	mgr.add_child(_indicator)
	_indicator.visible = false


func exit(mgr) -> void:
	if _indicator:
		_indicator.queue_free()
		_indicator = null


func process_state(mgr, delta: float) -> void:
	_last_ground_hit = mgr.get_floor_hit_at_mouse()
	if _indicator == null:
		return
	if _last_ground_hit == null:
		_indicator.visible = false
	else:
		_indicator.visible = true
		_indicator.global_position = _last_ground_hit


func handle_object_clicked(mgr, object: Node3D, event: InputEvent, click_position: Vector3) -> void:
	_try_confirm(mgr, event)


func handle_ground_clicked(mgr, click_position: Vector3, event: InputEvent) -> void:
	_try_confirm(mgr, event)


func handle_cancel(mgr) -> void:
	mgr.enter_default()


func _try_confirm(mgr, event: InputEvent) -> void:
	if not event.is_action_pressed("normal_action"):
		return
	if _last_ground_hit == null:
		return
	ConnectionManager.activate_skill(skill.skill_id, skill_level, _last_ground_hit)
	mgr.enter_default()
