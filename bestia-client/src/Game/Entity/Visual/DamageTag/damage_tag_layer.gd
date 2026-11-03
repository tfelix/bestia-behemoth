extends Node3D
class_name DamageTagLayer
## Shows every damage number in the world. One loop drives them all and their labels are pooled, because
## an area fight can raise hundreds within a second, and a node, tween and free per hit adds up.
##
## The tags live here rather than under the entity that was hit, so a killing blow outlives the corpse.

const DamageTagScn = preload("res://Game/Entity/Visual/DamageTag/DamageTag.tscn")

## Each live tag costs two draw calls (text and outline), as transparent labels are never batched.
const _MAX_TAGS := 200
const _MAX_DISTANCE := 60.0

## In spawn order, so the oldest comes first.
var _live: Array[DamageTag] = []
var _idle_labels: Array[Label3D] = []
## The side the last tag over each target flew to, by target id, so that tags with no attacker in
## view alternate.
var _last_sides: Dictionary[int, float] = {}


static func get_instance() -> DamageTagLayer:
	var loop := Engine.get_main_loop() as SceneTree
	return loop.get_first_node_in_group("damage_tag_layer") as DamageTagLayer


func _enter_tree() -> void:
	add_to_group("damage_tag_layer")


func show_damage(msg: DamageEntitySMSG, at: Vector3) -> void:
	var entities := EntityManager.get_instance()
	var attacker: Entity = entities.get_entity(msg.SourceEntityId) if entities != null else null
	spawn(msg, at, attacker.global_position if attacker != null else null)


## [param attacker_at] is null when the attacker is unknown here.
func spawn(msg: DamageEntitySMSG, at: Vector3, attacker_at: Variant) -> void:
	var camera := get_viewport().get_camera_3d()
	if camera == null or not _is_seen(camera, at):
		return
	var tag := DamageTag.new(_claim_label())
	tag.start(msg, at, _side_away_from(attacker_at, at, camera, msg.EntityId), camera)
	_push_up_older(tag)
	_live.append(tag)


func _process(delta: float) -> void:
	if _live.is_empty():
		return
	var camera := get_viewport().get_camera_3d()
	for i in range(_live.size() - 1, -1, -1):
		if camera == null or not _live[i].advance(delta, camera):
			_release(i)


func _is_seen(camera: Camera3D, at: Vector3) -> bool:
	return camera.global_position.distance_to(at) <= _MAX_DISTANCE and camera.is_position_in_frustum(at)


## Tags fly away from whoever struck, as if knocked off. Without one in view they alternate sides.
func _side_away_from(attacker_at: Variant, at: Vector3, camera: Camera3D, target_id: int) -> float:
	var side: float = -_last_sides.get(target_id, -1.0)
	if attacker_at != null and not camera.is_position_behind(attacker_at):
		var screen_gap := camera.unproject_position(at).x - camera.unproject_position(attacker_at).x
		if absf(screen_gap) >= 1.0:
			side = signf(screen_gap)
	if _last_sides.size() > _MAX_TAGS:
		_last_sides.clear()
	_last_sides[target_id] = side
	return side


## A younger tag on the same side of the same target would land on the older ones, so they make room.
func _push_up_older(tag: DamageTag) -> void:
	for older in _live:
		if older.target_id == tag.target_id and older.side == tag.side:
			older.push_up(tag.line_height())


## A pooled label, or the oldest tag's once all are in use.
func _claim_label() -> Label3D:
	if _live.size() >= _MAX_TAGS:
		_release(0)
	if not _idle_labels.is_empty():
		return _idle_labels.pop_back()
	var label: Label3D = DamageTagScn.instantiate()
	add_child(label)
	return label


func _release(index: int) -> void:
	var label := _live[index].label
	label.hide()
	_idle_labels.append(label)
	_live.remove_at(index)
