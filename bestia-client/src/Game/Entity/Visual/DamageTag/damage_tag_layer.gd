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


static func get_instance() -> DamageTagLayer:
	var loop := Engine.get_main_loop() as SceneTree
	return loop.get_first_node_in_group("damage_tag_layer") as DamageTagLayer


func _enter_tree() -> void:
	add_to_group("damage_tag_layer")


func show_damage(msg: DamageEntitySMSG, at: Vector3) -> void:
	var camera := get_viewport().get_camera_3d()
	if camera == null or not _is_seen(camera, at):
		return
	var tag := DamageTag.new(_claim_label())
	tag.start(msg, at, camera)
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
