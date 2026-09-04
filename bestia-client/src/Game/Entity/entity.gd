class_name Entity extends Node3D

# Movement prediction
# ===================
# Entities follow server-sent paths client-side with the same integrator as the server's MoveSystem.
# Waypoints arrive once per walk (PathComponentSMSG; an empty one means "stopped" and says where),
# plus a resync position every MoveSystem.POSITION_RESYNC_STEPS tiles and a speed in tiles/second.
#
# `position` is _logical_position plus half a tile horizontally (a tile coordinate names its corner,
# see TileSpace) and _ground_offset vertically. Everything else here works in logical space, so use
# get_logical_position() outside this file - rounding `position` back can land a tile away.


var BestiaModelScn = preload("res://Game/Entity/Visual/BestiaVisual/BestiaVisual.tscn")
var MasterModelScn = preload("res://Game/Entity/Visual/MasterVisual/MasterVisual.tscn")
var Camera = preload("res://Game/SpringArmCamera/SpringArmCamera.tscn")

var entity_id: int = 0

# Latest state pushed by the server, cached because the window or HUD showing it may be closed.
#
# Buffs and debuffs (BuffListEntry).
var _effects: Array = []

# Skill points (masters only).
var _skill_points: int = 0

# Worn items by EquipmentSlot ordinal: {slot: {"item_id": int, "unique_id": int}}.
var _equipment: Dictionary = {}

# Effective base status values: {"strength": int, ...}.
var _status_values: Dictionary = {}

# Unbuffed status values, same keys - the next point's cost is priced off these, not the buffed ones.
var _base_status_values: Dictionary = {}

# Status points (masters only).
var _status_points: int = 0

# Condition pools, null until the first push - a master spawns full, so that may be its only one.
var _health: ConditionPool = null
var _mana: ConditionPool = null
var _stamina: ConditionPool = null

# The last health push, replayed into a visual that was built after it arrived - see _seed_visual.
var _last_health_msg: HealthComponentSMSG = null

# True while lying dead awaiting respawn (player bodies only; mobs vanish). Gates the walk/idle pose.
var _is_dead: bool = false

## Where this entity is, in words. Owner-only on the wire, so null on all but the local master.
var _place: PlaceComponentSMSG = null

# True while channelling a skill; on the owned entity it also blocks movement input.
var _casting: bool = false

var _camera: Node3D = null
var _speed: float = 2.5


# Prediction in "tile steps", like the server's MoveSystem: every waypoint counts as 1.0, straight
# or diagonal, so diagonals run faster in world space here too and the server's updates line up.
var _nodes: Array[Vector3] = []      # [anchor, wp1, wp2, ...]
var _progress: float = 0.0           # continuous index into _nodes
var _error: float = 0.0              # outstanding server correction, in tile steps
var _is_moving: bool = false
var _faced_seg: int = -1

# Visual facing rotation
var _visual_rotation_start_basis: Basis = Basis.IDENTITY
var _visual_rotation_target_basis: Basis = Basis.IDENTITY
var _visual_rotation_start_time: float = 0.0
var _visual_rotating: bool = false
# Guards against resetting the walk animation twice, which could cancel a server animation.
var _has_reset_walk_anim: bool = false

# The server's posture, outranked only by death. "IDLE" means it asserts nothing and the local
# walk/idle heuristic owns the pose. Walk and idle never arrive here - sleeping and the like do.
const _POSTURE_NONE = "IDLE"
var _posture: String = _POSTURE_NONE


const _CORRECTION_IGNORE: float = 0.4    # steps of desync trusted as latency, not error
const _CORRECTION_TIME: float = 0.25     # seconds to bleed a correction back in
const _CORRECTION_MAX_BOOST: float = 2.5 # cap on extra steps/sec while catching up
const _SNAP_STEPS: float = 2.5           # desync this large just snaps
const _ROTATION_DURATION: float = 0.3  # Time to turn the model to face movement direction
const _VISUAL_NODE_NAME = "Visual"

# Ground snapping (see _update_ground_offset).
const _GROUND_MAX_RATE: float = 6.0      # metres/second the correction may travel
const _GROUND_MAX_OFFSET: float = 1.0    # metres of correction the terrain is trusted for

# Where the server says this entity is: whole-voxel coordinates in Godot's axis order, lerped
# between two of them while moving. `position` is this plus the drawing corrections.
var _logical_position: Vector3 = Vector3.ZERO

# Height correction that puts the feet on the drawn terrain - see _update_ground_offset.
var _ground_offset: float = 0.0


func _process(delta: float) -> void:
	_update_movement(delta)
	_update_visual_rotation()
	_update_ground_offset(delta)


func _update_movement(delta: float) -> void:
	# A corpse neither walks nor idles - its pose belongs to the visual.
	if _is_dead:
		return

	# Likewise an asserted posture (asleep, say). Movement still runs, so it slides rather than sits.
	var pose_is_ours := _posture == _POSTURE_NONE

	if not _is_moving or _nodes.size() < 2 or _speed <= 0.0:
		if not _has_reset_walk_anim:
			_has_reset_walk_anim = true
			if pose_is_ours:
				_update_animation_from_client("IDLE")
		return
	else:
		_has_reset_walk_anim = false
		if pose_is_ours:
			_update_animation_from_client("WALK")

	var last := _nodes.size() - 1

	var step := _speed * delta
	if absf(_error) > _SNAP_STEPS:
		# Way out of sync (teleport, long stall, dropped packets): jump.
		step += _error
		_error = 0.0
	elif absf(_error) > 0.0001:
		# Fold part of the error into this advance, clamped so we never travel backwards.
		var corr := _error * minf(1.0, delta / _CORRECTION_TIME)
		corr = clampf(corr, -_speed * delta, _CORRECTION_MAX_BOOST * delta)
		step += corr
		_error -= corr

	step = maxf(step, 0.0)
	_progress = minf(_progress + step, float(last))

	var seg := mini(int(_progress), last - 1)
	var frac := _progress - float(seg)
	_logical_position = _nodes[seg].lerp(_nodes[seg + 1], frac)
	_apply_position()

	if seg != _faced_seg:
		_face_direction(_nodes[seg + 1] - _nodes[seg])
		_faced_seg = seg

	if _progress >= float(last) and absf(_error) < 0.0001:
		_is_moving = false


## Where the server thinks this entity is, without the ground correction `position` carries.
func get_logical_position() -> Vector3:
	return _logical_position


## Draws the entity in the middle of its tile, lifted onto the terrain.
func _apply_position() -> void:
	position = _logical_position + TileSpace.CENTRE_OFFSET + Vector3(0.0, _ground_offset, 0.0)


## Sub-voxel height correction, applied to this node so the camera on its spring arm follows too.
## Server heights are whole voxels while the terrain is drawn sub-voxel, so a walk would otherwise
## ramp between whole numbers over curved ground and read as stepping.
func _update_ground_offset(delta: float) -> void:
	var target := _probe_ground_offset()
	if is_nan(target):
		# Terrain not streamed yet, or swimming/falling: hold the last offset rather than dropping.
		return

	# Rate limited rather than smoothed: a filter's lag would put back the soft corner at every
	# waypoint this exists to remove. The limit only bites on a carve underfoot or a chunk arriving.
	var moved := move_toward(_ground_offset, target, _GROUND_MAX_RATE * delta)

	# move_toward lands exactly on target, so equality is safe and a settled entity skips the write.
	if moved == _ground_offset:
		return

	_ground_offset = moved
	_apply_position()


## The correction the terrain wants right now, or NAN if it cannot be known. Clamped, because an
## honest answer is never much more than half a voxel.
func _probe_ground_offset() -> float:
	if ConnectionManager.chunk_stream == null:
		return NAN

	# Probed under the model, where the feet are, and off the logical height - feeding the corrected
	# one back would make the offset measure itself.
	var ground: float = ConnectionManager.chunk_stream.GroundYAt(
		_logical_position.x + TileSpace.CENTRE_OFFSET.x,
		_logical_position.z + TileSpace.CENTRE_OFFSET.z,
		_logical_position.y)
	if is_nan(ground):
		return NAN

	return clampf(ground - _logical_position.y, -_GROUND_MAX_OFFSET, _GROUND_MAX_OFFSET)


## Puts the entity on the ground this frame instead of easing onto it - for teleports.
func _snap_ground_offset() -> void:
	var target := _probe_ground_offset()
	if not is_nan(target):
		_ground_offset = target

	_apply_position()


## Which way the model faces, flat and unit length, or [constant Vector3.ZERO] when there is no
## visual - read off the Visual, since this node never turns and the model's front is +Z.
func facing() -> Vector3:
	var visual := get_node_or_null(_VISUAL_NODE_NAME) as Node3D
	if visual == null:
		return Vector3.ZERO

	var forward := visual.global_transform.basis.z

	# normalized() of a zero vector is zero in Godot 4, so a degenerate basis also reads as nowhere.
	return Vector3(forward.x, 0.0, forward.z).normalized()


func _face_direction(direction: Vector3) -> void:
	# Yaw only, so the model does not pitch on sloped segments.
	var flat_direction = Vector3(direction.x, 0.0, direction.z)
	if flat_direction.length_squared() < 0.0001:
		return

	var visual = get_node_or_null(_VISUAL_NODE_NAME)
	if visual == null:
		return

	# use_model_front = true since the model's forward axis is +Z, not Godot's default -Z.
	var target_basis = Basis.looking_at(flat_direction, Vector3.UP, true)
	_visual_rotation_start_basis = visual.transform.basis
	_visual_rotation_target_basis = target_basis
	_visual_rotation_start_time = Time.get_ticks_msec() / 1000.0
	_visual_rotating = true


func _update_visual_rotation() -> void:
	if not _visual_rotating:
		return

	var visual = get_node_or_null(_VISUAL_NODE_NAME)
	if visual == null:
		_visual_rotating = false
		return

	var current_time = Time.get_ticks_msec() / 1000.0
	var progress = (current_time - _visual_rotation_start_time) / _ROTATION_DURATION

	if progress >= 1.0:
		visual.transform.basis = _visual_rotation_target_basis
		_visual_rotating = false
	else:
		visual.transform.basis = _visual_rotation_start_basis.slerp(_visual_rotation_target_basis, _ease_out_cubic(progress))


func show_chat(msg: ChatSMSG) -> void:
	var visual = _get_visual_for_method("show_chat")
	if visual != null:
		visual.show_chat(msg)
	else:
		printerr("Entity %s has no show_chat visual", [entity_id])


## Builds the visual from a kind plus a catalogue id. Masters go through update_master_visual.
func update_visual(msg: VisualComponentSMSG) -> void:
	var scene: PackedScene = _visual_scene_for(msg)
	if scene == null:
		return

	_release_visual()

	# Untyped on purpose: each kind's visual declares its own setup_visual, taking its own message.
	var visual = scene.instantiate()
	visual.setup_visual(msg)
	visual.name = _VISUAL_NODE_NAME
	add_child(visual)
	_seed_visual(visual)


func _visual_scene_for(msg: VisualComponentSMSG) -> PackedScene:
	match msg.Kind:
		VisualKind.BESTIA:
			return BestiaModelScn
		VisualKind.ITEM:
			var item_resource = ItemDB.get_instance().get_item(msg.VisualId)
			# An unknown id is a real desync; a known item without a mesh gets a placeholder instead.
			if item_resource == null:
				printerr("Entity %s: no item %s in the ItemDB" % [entity_id, msg.VisualId])
				return null
			return item_resource.get_item_visual()
		VisualKind.EFFECT:
			var effect_resource = EffectDB.get_instance().get_effect(msg.VisualId)
			if effect_resource == null or effect_resource.effect_visual == null:
				printerr("Entity %s: no effect_visual PackedScene for effect %s" % [entity_id, msg.VisualId])
				return null
			return effect_resource.effect_visual
		_:
			printerr("Entity %s: unknown visual kind %s" % [entity_id, msg.Kind])
			return null


func update_master_visual(msg: MasterVisualComponentSMSG) -> void:
	_release_visual()
	var visual = MasterModelScn.instantiate() as MasterVisual
	visual.setup_visual(msg)
	visual.name = _VISUAL_NODE_NAME
	add_child(visual)
	_seed_visual(visual)


## Frees the attached visual, giving up its name first.
##
## queue_free is deferred to the end of the frame, so a replacement added in the same frame would find the
## name still taken - Godot renames the newcomer, and _VISUAL_NODE_NAME then resolves to the node that is
## about to disappear, and to null after that.
func _release_visual() -> void:
	var existing = get_node_or_null(_VISUAL_NODE_NAME)
	if existing == null:
		return

	existing.name = "VisualOutgoing"
	existing.queue_free()


## Pushes state that arrived before this visual existed into it. Only health needs it so far.
func _seed_visual(visual: Node) -> void:
	if _last_health_msg != null and visual.has_method("update_health"):
		visual.update_health(_last_health_msg)


func set_selected(is_selected: bool) -> void:
	print("Entity: set_selected: %s" % [is_selected])


## Called by mouse_manager when this entity is clicked; may delegate to the visual.
func on_interact() -> void:
	print("Entity: on_interact")


func update_position(msg: PositionComponent) -> void:
	var new_position: Vector3 = msg.Position

	if not _is_moving:
		# Nothing to reconcile against, just snap.
		_logical_position = new_position
		_error = 0.0
		_snap_ground_offset()
		return

	var idx := _index_of_node(new_position)
	if idx < 0:
		# Server is off our predicted path (new route/teleport): snap and stop.
		_logical_position = new_position
		_nodes = [new_position]
		_is_moving = false
		_error = 0.0
		_snap_ground_offset()
		return

	# Reconcile in the progress domain: the gap to the server's node becomes an error we bleed off.
	var e := float(idx) - _progress
	if absf(e) <= _CORRECTION_IGNORE:
		# Within latency noise, trust our own prediction.
		return
	_error = e


## A walk, or - with an empty path - the end of one. Sent once per walk, not per tile step.
func update_path(msg: PathComponentSMSG) -> void:
	if msg.Path.is_empty():
		_stop_at(msg)
		return

	# Anchor on the logical position - the ground-corrected height would apply that correction twice.
	_nodes.clear()
	_nodes.append(_logical_position)
	for vec3 in msg.Path:
		_nodes.append(vec3)

	# Not zero: the server may be telling us about a walk already under way, and _nodes[0] is the tile it
	# last reached rather than where it stands.
	_progress = clampf(msg.StartOffset, 0.0, 1.0)
	_error = 0.0
	_faced_seg = -1
	_is_moving = _nodes.size() >= 2


## The walk is over - finished, or cut short by combat, sleep, death, a stop or a teleport. Only the
## first leaves us where the server is, hence the stop position; snapped, since _error is dropped.
func _stop_at(msg: PathComponentSMSG) -> void:
	_is_moving = false
	_progress = 0.0
	_error = 0.0
	_faced_seg = -1

	if msg.HasStopPosition:
		_logical_position = msg.StopPosition
		_snap_ground_offset()   # applies the position too

	_nodes.clear()
	_nodes.append(_logical_position)


func update_speed(msg: SpeedComponentSMSG) -> void:
	# Progress is decoupled from elapsed time, so a new speed just applies from the next frame.
	_speed = msg.Speed
	if _speed <= 0.0:
		_is_moving = false


## Lying dead, or back on its feet. Also stops the local walk/idle heuristic in _update_movement.
func update_dead(msg: DeadComponentSMSG) -> void:
	_is_dead = not msg.Removed

	# Going down interrupts the walk: the body stays where it fell, so a leftover path would drag it.
	if _is_dead:
		_is_moving = false
		_has_reset_walk_anim = false

	var visual = get_node_or_null(_VISUAL_NODE_NAME)
	if visual != null and visual.has_method("set_dead"):
		visual.set_dead(_is_dead)


## Whether this entity is a body waiting to respawn.
func is_dead() -> bool:
	return _is_dead


## A posture from the server: a pose no observer could work out for itself. Walk and idle are not
## among them. "IDLE" drops the claim and hands the pose back to the local heuristic.
func update_animation(msg: AnimationComponentSMSG) -> void:
	_posture = msg.Kind.to_upper()

	# The death pose outranks every posture, and one may still be in flight from the tick it died.
	if _is_dead:
		return

	if _posture == _POSTURE_NONE:
		# Nothing to play, but _update_movement must re-assert over a stale _has_reset_walk_anim.
		_has_reset_walk_anim = false
		return

	var visual = _get_visual_for_method("update_animation")
	if visual != null:
		visual.update_animation(msg)

# Called from client code, not the server, when an action implies the animation (e.g. a new path).
func _update_animation_from_client(animation_name: String) -> void:
	var visual = _get_visual_for_method("update_animation_from_client")
	if visual != null:
		visual.update_animation_from_client(animation_name)


func _index_of_node(p: Vector3) -> int:
	var best := -1
	var best_d := 0.5   # must land within half a tile to count as "this node"
	for i in _nodes.size():
		var d := Vector2(_nodes[i].x - p.x, _nodes[i].z - p.z).length()
		if d < best_d:
			best_d = d
			best = i
	return best


func show_damage(msg: DamageEntitySMSG) -> void:
	var visual = _get_visual_for_method("show_damage")
	if visual != null:
		visual.show_damage(msg)


func update_health(msg: HealthComponentSMSG) -> void:
	_health = ConditionPool.new(msg.Current, msg.Max)
	_last_health_msg = msg
	# Arriving before the visual exists is the normal race, not an error - _seed_visual replays it.
	var visual = get_node_or_null(_VISUAL_NODE_NAME)
	if visual != null and visual.has_method("update_health"):
		visual.update_health(msg)


## Mana and stamina have no visual of their own; cached only so the owner's HUD can seed itself.
func update_mana(msg: ManaComponentSMSG) -> void:
	_mana = ConditionPool.new(msg.Current, msg.Max)


func update_stamina(msg: StaminaComponentSMSG) -> void:
	_stamina = ConditionPool.new(msg.Current, msg.Max)


## Cached like mana: pushed on spawn and then only on a border crossing, so a late HUD needs it.
func update_place(msg: PlaceComponentSMSG) -> void:
	_place = msg


func get_place() -> PlaceComponentSMSG:
	return _place


## Null until the server has pushed this pool at least once - a missing pool is not 0/0.
func get_health() -> ConditionPool:
	return _health


func get_mana() -> ConditionPool:
	return _mana


func get_stamina() -> ConditionPool:
	return _stamina


func update_casting(msg: CastingComponentSMSG) -> void:
	_casting = true
	var visual = _get_visual_for_method("update_casting")
	if visual != null:
		visual.update_casting(msg)


## The Casting component was removed - the cast completed or was interrupted, which look the same.
func clear_casting() -> void:
	_casting = false
	var visual = _get_visual_for_method("clear_casting")
	if visual != null:
		visual.clear_casting()


## Movement clicks are suppressed while this is true - see ConnectionManager.move_to.
func is_casting() -> bool:
	return _casting


## Whether the predicted walk is still running. Good for spotting one that stopped short, no good
## for detecting arrival - measure the distance for that.
func is_moving() -> bool:
	return _is_moving


func update_effects(msg: BuffListSMSG) -> void:
	_effects = msg.Effects


func get_effects() -> Array:
	return _effects


func update_skill_points(msg: SkillPointsComponentSMSG) -> void:
	_skill_points = msg.Points


func get_skill_points() -> int:
	return _skill_points


func update_equipment(msg: EquipmentComponentSMSG) -> void:
	var by_slot: Dictionary = {}
	for equipped in msg.Items:
		by_slot[int(equipped.Slot)] = {
			"item_id": int(equipped.ItemId),
			"unique_id": int(equipped.UniqueId),
		}
	_equipment = by_slot


func get_equipment() -> Dictionary:
	return _equipment


func update_status_values(msg: StatusValuesComponentSMSG) -> void:
	_status_values = {
		"strength": msg.Strength,
		"vitality": msg.Vitality,
		"intelligence": msg.Intelligence,
		"dexterity": msg.Dexterity,
		"willpower": msg.Willpower,
		"agility": msg.Agility,
	}


func get_status_values() -> Dictionary:
	return _status_values


func update_base_status_values(msg: BaseStatusValuesComponentSMSG) -> void:
	_base_status_values = {
		"strength": msg.Strength,
		"vitality": msg.Vitality,
		"intelligence": msg.Intelligence,
		"dexterity": msg.Dexterity,
		"willpower": msg.Willpower,
		"agility": msg.Agility,
	}


func get_base_status_values() -> Dictionary:
	return _base_status_values


func update_status_points(msg: StatusPointsComponentSMSG) -> void:
	_status_points = msg.Points


func get_status_points() -> int:
	return _status_points


func select_for_active() -> void:
	_camera = Camera.instantiate()
	add_child(_camera)


func _get_visual_for_method(method_name: String) -> Visual:
	var visual = get_node_or_null(_VISUAL_NODE_NAME)
	if visual != null && visual.has_method(method_name):
		return visual
	else:
		printerr("Entity %s visual method %s handler missing" % [entity_id, method_name])
		return null


func vanish(msg: VanishEntitySMSG) -> void:
	var visual = get_node_or_null(_VISUAL_NODE_NAME)
	if visual != null && visual.has_method("vanish"):
		visual.vanish(msg)
	else:
		print("Entity: Entity %s no vanish handler, simply remove it" % [entity_id])
		queue_free()


func remove_as_active() -> void:
	if _camera != null:
		_camera.queue_free()
		_camera = null


func _ease_out_cubic(t: float) -> float:
	# Cubic ease-out function for smooth corrections
	var f = t - 1.0
	return f * f * f + 1.0
