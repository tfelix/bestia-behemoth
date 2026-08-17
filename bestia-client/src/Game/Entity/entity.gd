class_name Entity extends Node3D

# Movement Prediction System
# ========================
# This system handles client-side movement prediction for networked entities.
#
# Key Features:
# - Follows server-provided paths at specified speeds (tiles/second)
# - Smoothly corrects to server-authoritative positions when they arrive
# - Handles edge cases like speed changes during movement and new paths
#
# Server Communication:
# - PositionComponent: Authoritative position updates (whole coordinates only)
# - PathComponentSMSG: Array of waypoints to follow
# - SpeedComponentSMSG: Movement speed in tiles per second (1 tile = 1 meter)
#
# The server only sends position updates for whole coordinates (e.g., x:1, y:5),
# never for fractional positions. Client handles smooth movement between tiles.
#
# Logical vs. drawn position
# ==========================
# `position` is NOT where the server says this entity is. It is _logical_position - which is - plus
# a sub-voxel ground correction, because whole-voxel heights do not meet the terrain that gets
# drawn. Everything in the prediction system below works in logical space; only _apply_position
# crosses over. Anything outside this file that needs a server coordinate must ask
# get_logical_position() rather than reading `position`, or a round trip through Vec3Convert's
# rounding can come back a tile away. See _update_ground_offset.


var BestiaModelScn = preload("res://Game/Entity/Visual/BestiaVisual/BestiaVisual.tscn")
var MasterModelScn = preload("res://Game/Entity/Visual/MasterVisual/MasterVisual.tscn")
var Camera = preload("res://Game/SpringArmCamera/SpringArmCamera.tscn")

var entity_id: int = 0

# Latest known buff/debuff list (BuffListEntry), cached here so BuffList can seed
# itself with whatever we already know as soon as this entity gets selected,
# without waiting for the next BuffListSMSG to arrive.
var _effects: Array = []

# Latest known available skill points (master entities only - bestias never spend
# points via this system), cached here for the same reason as _buffs: the Skills
# window may not be open (or may be showing a different bestia's tree) when the
# server pushes an update, so it needs somewhere durable to seed itself from.
var _skill_points: int = 0

# Latest known worn items, by EquipmentSlot ordinal ({slot: {"item_id": int, "unique_id":
# int}}), cached here for the same reason as _skill_points: the Equipment window may not be
# open (or may be showing a different entity) when the server pushes an update, so it needs
# somewhere durable to seed itself from.
var _equipment: Dictionary = {}

# Latest known effective base status values ({"strength": int, "vitality": int, "intelligence":
# int, "dexterity": int, "willpower": int, "agility": int}), cached here for the same reason as
# _skill_points: the StatusPoints window may not be open when the server pushes an update, so it
# needs somewhere durable to seed itself from.
var _status_values: Dictionary = {}

# Latest known *unbuffed* status values, same keys as _status_values. Kept apart from the effective
# ones because the cost of the next status point is priced off the base value - pricing it off a
# buffed value would make a point cost more for as long as the buff happened to be running.
var _base_status_values: Dictionary = {}

# Latest known available status points (master entities only, same reason as _skill_points).
var _status_points: int = 0

# Latest known condition pools, cached for the same reason as _skill_points, but with a sharper edge
# to it: a master spawns with its pools already full, so the server's push at spawn is the *only* one
# until something damages it, and MasterProfile is often still being loaded when that push lands.
# Without a cache here the HUD has nothing to seed from and keeps showing its scene placeholders.
# Null until the first push for that pool arrives - see ConditionPool on why absent is not zero.
var _health: ConditionPool = null
var _mana: ConditionPool = null
var _stamina: ConditionPool = null

# True while this entity is channelling a skill (server-driven via CastingComponentSMSG, cleared by
# a CastingComponentSMSG with Removed = true). For the owned entity this also gates movement input,
# since moving cancels the cast server-side.
var _casting: bool = false

var _camera: Node3D = null
##
var _speed: float = 2.5


# Movement prediction: follow the server's path in "tile steps", matching the
# server integrator (MoveSystem advances a fraction by speed*dt and treats every
# waypoint - straight OR diagonal - as exactly 1.0). Diagonals are therefore
# faster in world space, exactly as on the server, so authoritative position
# updates line up with our prediction instead of fighting it.
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
# This flag prevents resetting the walk animation twice so we do not run into
# the problem that we might cancel out any other animation that comes in from
# the server.
var _has_reset_walk_anim: bool = false


const _CORRECTION_IGNORE: float = 0.4    # steps of desync trusted as latency, not error
const _CORRECTION_TIME: float = 0.25     # seconds to bleed a correction back in
const _CORRECTION_MAX_BOOST: float = 2.5 # cap on extra steps/sec while catching up
const _SNAP_STEPS: float = 2.5           # desync this large just snaps
const _ROTATION_DURATION: float = 0.3  # Time to turn the model to face movement direction
const _VISUAL_NODE_NAME = "Visual"

# Ground snapping (see _update_ground_offset).
const _GROUND_MAX_RATE: float = 6.0      # metres/second the correction may travel
const _GROUND_MAX_OFFSET: float = 1.0    # metres of correction the terrain is trusted for

# Where the server says this entity is: whole-voxel coordinates in Godot's axis order, and for a
# moving entity the lerp between two of them. `position` is this plus _ground_offset.
#
# The split is the point. "Which tile am I on" is the server's to answer and is what every path,
# distance and reconciliation is built from; "where do I draw" has to meet terrain that whole-voxel
# coordinates cannot express. Keeping them in one field means one of the two questions gets the
# wrong answer, so they are two fields.
var _logical_position: Vector3 = Vector3.ZERO

# How far the entity is lifted or dropped from that, so its feet meet the terrain that is actually
# drawn. Applied to this node, which is what puts the camera on it too - see _update_ground_offset.
var _ground_offset: float = 0.0


func _process(delta: float) -> void:
	_update_movement(delta)
	_update_visual_rotation()
	_update_ground_offset(delta)


func _update_movement(delta: float) -> void:
	if not _is_moving or _nodes.size() < 2 or _speed <= 0.0:
		if not _has_reset_walk_anim:
			_has_reset_walk_anim = true
			_update_animation_direct("IDLE")
		return
	else:
		_has_reset_walk_anim = false
		_update_animation_direct("WALK")

	var last := _nodes.size() - 1

	var step := _speed * delta
	if absf(_error) > _SNAP_STEPS:
		# Way out of sync (teleport, long stall, dropped packets): jump.
		step += _error
		_error = 0.0
	elif absf(_error) > 0.0001:
		# Speed up (behind) or slow down (ahead) by folding part of the error
		# into this frame's advance. Clamped so we never travel backwards.
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
##
## Anything converting back to server coordinates wants this one - a whole-voxel round trip through
## the corrected position can land a tile boundary away.
func get_logical_position() -> Vector3:
	return _logical_position


## Draws the entity at its logical position, lifted onto the terrain.
func _apply_position() -> void:
	position = _logical_position + Vector3(0.0, _ground_offset, 0.0)


## Sub-voxel height correction, applied to this node so that everything hanging off it follows.
##
## The height the server sends is a whole voxel - ChunkCoords.standingZ rounds the real elevation -
## while the terrain is drawn at the sub-voxel height the generator wrote and then nudged again by
## the mesher's corner averaging. Walking a path therefore ramps between whole numbers while the
## ground underneath curves, and the mismatch reads as the entity stepping rather than walking.
##
## Correcting the [b]Visual[/b] child instead would fix the model and nothing else, which is not
## enough: the camera's spring arm is a child of this node, so it would keep climbing in whole-voxel
## steps while the model it is pointed at moved smoothly. The correction has to be on the transform
## the camera inherits, which is this one.
##
## `position` is therefore no longer the server's answer to where this entity is - see
## [method get_logical_position], which is. Movement prediction is unaffected either way: `_nodes`
## and `_progress` are in logical space throughout, so nothing here can desync us.
func _update_ground_offset(delta: float) -> void:
	var target := _probe_ground_offset()
	if is_nan(target):
		# Terrain we have not been sent yet, or nothing to stand on within reach - a swimming or
		# falling entity. Hold the last offset rather than easing back to zero, so a streaming
		# hiccup does not show up as the entity dropping and rising again.
		return

	# Rate limited rather than exponentially smoothed, and the difference matters now that the camera
	# rides on this. An exponential filter always lags its target, and the target here moves fast -
	# it is the gap between a straight ramp and a curved surface, which swings most of a metre over a
	# single tile step. That lag would put a soft corner back at every waypoint, which is the exact
	# artefact this exists to remove. A speed limit has no lag at all while the correction is moving
	# slower than the limit, which is every ordinary step, and only bites on a genuine discontinuity
	# - a carve under our feet, or a chunk arriving - where easing in over a few frames is what is
	# wanted anyway.
	var moved := move_toward(_ground_offset, target, _GROUND_MAX_RATE * delta)

	# Exact equality is the right test and not a float sin: move_toward steps by a fixed amount or
	# lands on the target, so a settled offset compares equal to itself bit for bit. Worth the branch
	# because _update_movement has already written this frame's position if we are walking, and a
	# standing entity should not dirty its transform sixty times a second for no change.
	if moved == _ground_offset:
		return

	_ground_offset = moved
	_apply_position()


## The correction the terrain wants right now, or NAN if it cannot be known.
##
## Clamped because a probe that finds the wrong surface should look like an entity standing slightly
## wrong, not like one buried in the ground: the honest answer is never more than about half a
## voxel, so anything past a whole one is a disagreement worth ignoring rather than obeying.
func _probe_ground_offset() -> float:
	if ConnectionManager.chunk_stream == null:
		return NAN

	# Probed at the logical position, not the drawn one. Feeding the corrected height back in would
	# make the offset measure itself and decay to nothing.
	var ground: float = ConnectionManager.chunk_stream.GroundYAt(
		_logical_position.x, _logical_position.z, _logical_position.y)
	if is_nan(ground):
		return NAN

	return clampf(ground - _logical_position.y, -_GROUND_MAX_OFFSET, _GROUND_MAX_OFFSET)


## Puts the entity on the ground this frame instead of easing onto it.
##
## For teleports, where the old offset belongs to terrain on the other side of the map and easing
## across would be seen as the entity sinking into place after it arrived.
func _snap_ground_offset() -> void:
	var target := _probe_ground_offset()
	if not is_nan(target):
		_ground_offset = target

	_apply_position()


func _face_direction(direction: Vector3) -> void:
	# Only yaw the model towards the movement direction, ignore any vertical
	# component so it doesn't pitch up/down on sloped waypoint segments.
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


func update_bestia_visual(msg: BestiaVisualComponent) -> void:
	var existing = get_node_or_null(_VISUAL_NODE_NAME)
	if existing != null:
		existing.queue_free()
	var visual = BestiaModelScn.instantiate() as BestiaVisual
	visual.setup_visual(msg)
	visual.name = _VISUAL_NODE_NAME
	add_child(visual)


func update_item_visual(msg: ItemVisualComponentSMSG) -> void:
	var existing = get_node_or_null(_VISUAL_NODE_NAME)
	if existing != null:
		existing.queue_free()
	var item_resource = ItemDB.get_instance().get_item(msg.ItemId)
	if item_resource == null or item_resource.item_visual == null:
		printerr("Entity %s: no item_visual PackedScene for item %s" % [entity_id, msg.ItemId])
		return
	var visual = item_resource.item_visual.instantiate() as ItemVisual
	visual.setup_visual(msg)
	visual.name = _VISUAL_NODE_NAME
	add_child(visual)


func update_master_visual(msg: MasterVisualComponentSMSG) -> void:
	var existing = get_node_or_null(_VISUAL_NODE_NAME)
	if existing != null:
		existing.queue_free()
	var visual = MasterModelScn.instantiate() as MasterVisual
	visual.setup_visual(msg)
	visual.name = _VISUAL_NODE_NAME
	add_child(visual)


func set_selected(is_selected: bool) -> void:
	print("Entity: set_selected: %s" % [is_selected])


### This is called if you clicked on an entity via the mouse_manager. You
### can implement a further delegation maybe against the visual component how
### to handle the click.
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

	# Reconcile in the progress domain: the server just reached node idx, so the
	# gap to our own progress becomes an error we bleed into the movement rate.
	var e := float(idx) - _progress
	if absf(e) <= _CORRECTION_IGNORE:
		# Within latency noise, trust our own prediction.
		return
	_error = e


func update_path(msg: PathComponentSMSG) -> void:
	# Rebuild the node list anchored where we currently are, then follow it. The logical position
	# rather than the rendered one: the waypoints behind it are the server's whole voxels, and
	# anchoring the chain on a height that has been nudged onto the terrain would feed the correction
	# into the path and then correct it a second time.
	_nodes.clear()
	_nodes.append(_logical_position)
	for vec3 in msg.Path:
		_nodes.append(vec3)

	_progress = 0.0
	_error = 0.0
	_faced_seg = -1
	_is_moving = _nodes.size() >= 2


func update_speed(msg: SpeedComponentSMSG) -> void:
	# Progress is decoupled from elapsed time, so a speed change just takes effect
	# on the next frame - no re-timing needed.
	_speed = msg.Speed
	if _speed <= 0.0:
		_is_moving = false


## This is only used if you play an enum. Other animations are often indirectly 
## sourced from the current action. For example if an entity is activly moving 
## it automatically played the move animation. This avoids server desyncs.
func update_animation(msg: AnimationComponentSMSG) -> void:
	print_debug("Entity: %s set animation: %s" % [msg.Kind, entity_id])
	var visual = _get_visual_for_method("update_animation")
	if visual != null:
		visual.update_animation(msg)


func _update_animation_direct(animation_name: String) -> void:
	# print_debug("Entity: _update_animation_direct set animation: %s" % [animation_name])
	var visual = _get_visual_for_method("update_animation_direct")
	if visual != null:
		visual.update_animation_direct(animation_name)


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
	var visual = _get_visual_for_method("update_health")
	if visual != null:
		visual.update_health(msg)


## Mana and stamina have no visual of their own (no floating bar over an entity shows them) - they
## are cached purely so the owner's HUD can seed itself, unlike update_health above.
func update_mana(msg: ManaComponentSMSG) -> void:
	_mana = ConditionPool.new(msg.Current, msg.Max)


func update_stamina(msg: StaminaComponentSMSG) -> void:
	_stamina = ConditionPool.new(msg.Current, msg.Max)


## Null until the server has pushed this pool at least once, which is why callers must null-check
## rather than treat a missing pool as 0/0.
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


## The Casting component was removed: the cast either completed or was interrupted. Both look the
## same on the wire, since either way the bar just goes away and movement is unblocked again.
func clear_casting() -> void:
	_casting = false
	var visual = _get_visual_for_method("clear_casting")
	if visual != null:
		visual.clear_casting()


## Movement clicks are suppressed while this is true - see ConnectionManager.move_to.
func is_casting() -> bool:
	return _casting


## Whether this entity is walking a predicted path right now.
##
## [b]Prediction state, not truth.[/b] It goes false at the end of every path segment and the server can
## replace the path at any moment via PathComponentSMSG. So this answers "is the walk still running",
## which is useful for noticing one that has [i]stopped[/i] short - it is not a way to detect arrival.
## Anything that needs "am I there yet" should measure the distance.
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
		printerr("Entity %s visual method %s handler missing, dropping message" % [entity_id, method_name])
		return null


func vanish(msg: VanishEntitySMSG) -> void:
	# Check if a visual node can handle the vanish.
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
