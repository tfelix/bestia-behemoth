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
## Between two numbers of one flurry.
const _BLOW_INTERVAL_SECONDS := 0.12

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


## Shows [param msg] over [param anchor], which a visual places above its body. A flurry shows one number per
## blow, a moment apart, adding up to the total the server sent; each lands where the anchor is by then.
func show_damage_over(msg: DamageEntitySMSG, anchor: Node3D) -> void:
	var blows := maxi(1, msg.Div)
	for i in blows:
		if i > 0:
			await get_tree().create_timer(_BLOW_INTERVAL_SECONDS).timeout
			if not is_instance_valid(anchor) or not anchor.is_inside_tree():
				return
		show_damage(msg if blows == 1 else _blow_of(msg, i, blows), anchor.global_position)


## One blow's share of [param msg]; the last one carries what does not divide evenly.
static func _blow_of(msg: DamageEntitySMSG, index: int, blows: int) -> DamageEntitySMSG:
	var share := msg.Damage / blows
	var blow := DamageEntitySMSG.new()
	blow.EntityId = msg.EntityId
	blow.SourceEntityId = msg.SourceEntityId
	blow.AttackId = msg.AttackId
	blow.SkillLevel = msg.SkillLevel
	blow.Type = msg.Type
	blow.ProjectileItemId = msg.ProjectileItemId
	blow.Div = 1
	blow.Damage = share + (msg.Damage - share * blows if index == blows - 1 else 0)
	return blow


func show_damage(msg: DamageEntitySMSG, at: Vector3) -> void:
	var entities := EntityManager.get_instance()
	if entities == null:
		spawn(msg, at, null, 0, DamageTag.Involvement.DEALT)
		return
	var attacker := entities.get_entity(msg.SourceEntityId)
	var health := entities.get_health_of(msg.EntityId)
	spawn(
		msg,
		at,
		attacker.global_position if attacker != null else null,
		health.maximum if health != null else 0,
		_involvement_of(msg, entities),
	)


## [param attacker_at] is null and [param max_hp] 0 when they are unknown here.
func spawn(
	msg: DamageEntitySMSG, at: Vector3, attacker_at: Variant, max_hp: int, involvement: DamageTag.Involvement
) -> void:
	var camera := get_viewport().get_camera_3d()
	if camera == null or not _is_seen(camera, at):
		return
	var label := _claim_label(involvement)
	if label == null:
		return
	var tag := DamageTag.new(label)
	var side := _side_away_from(attacker_at, at, camera, msg.EntityId) if DamageTag.is_hit(msg.Type) else 0.0
	tag.start(msg, at, side, max_hp, involvement, camera)
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
			# Half of each: both are centred on their position.
			older.push_up((tag.line_height() + older.line_height()) * 0.5)


static func _involvement_of(msg: DamageEntitySMSG, entities: EntityManager) -> DamageTag.Involvement:
	if entities.is_owned_entity_id(msg.EntityId):
		return DamageTag.Involvement.TAKEN
	if entities.is_owned_entity_id(msg.SourceEntityId):
		return DamageTag.Involvement.DEALT
	return DamageTag.Involvement.BYSTANDER


## A pooled label, or the label of the oldest tag that matters less once all are in use. Null when
## every live tag matters more than the new one.
func _claim_label(involvement: DamageTag.Involvement) -> Label3D:
	if _live.size() >= _MAX_TAGS:
		var replaceable := _oldest_bystander()
		if replaceable < 0 and involvement != DamageTag.Involvement.BYSTANDER:
			replaceable = 0
		if replaceable < 0:
			return null
		_release(replaceable)
	if not _idle_labels.is_empty():
		return _idle_labels.pop_back()
	var label: Label3D = DamageTagScn.instantiate()
	add_child(label)
	return label


func _oldest_bystander() -> int:
	for i in _live.size():
		if _live[i].involvement == DamageTag.Involvement.BYSTANDER:
			return i
	return -1


func _release(index: int) -> void:
	var label := _live[index].label
	label.hide()
	_idle_labels.append(label)
	_live.remove_at(index)
