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


# Movement prediction: follow the server's path in metres of ground covered, matching MoveSystem, which
# charges each step the distance it actually spans - 1 for a cardinal step and sqrt(2) for a diagonal.
#
# Metres rather than a count of waypoints, and that is what makes the two agree. Advancing one waypoint per
# 1/speed seconds made diagonals 41% faster in world space; it also meant the partial first segment left by
# every re-anchor cost a whole step's worth of time, so the client ran slower than the server and lived on
# the correction below. See _arc.
var _nodes: Array[Vector3] = []      # [anchor, wp1, wp2, ...]
var _arc: Array[float] = []          # ground distance from _nodes[0] to each node, so _arc[0] is always 0
var _travelled: float = 0.0          # metres along _nodes
var _error: float = 0.0              # outstanding server correction, in metres
var _is_moving: bool = false
var _seg: int = 0                    # cursor into _nodes, so _segment_at need not rescan from zero
var _faced_seg: int = -1

# A walk this client started on its own click, still waiting for the server to confirm it. Empty on every
# entity but our own, and cleared the moment the server answers. See predict_path.
var _predicted_path: Array[Vector3] = []
var _prediction_deadline_msec: int = 0

# Whether the walk currently running is one we started ourselves, as opposed to one the server handed us for
# somebody else's entity. Outlives _predicted_path, which only covers the wait for confirmation: we stay a
# one-way trip ahead of the server for the whole walk, and update_position has to know that is expected.
var _is_predicted_walk: bool = false

# Where a walk we predicted is headed, while the server's own account of that walk is still arriving. Every
# position it reports is a trip old, so on a short walk they all land after we have finished it - and
# snapping to one would yank the entity back to a tile it has already left. Cleared by the empty path the
# server sends when it drops the path, which is the end of every walk.
var _awaiting_arrival: bool = false
var _predicted_destination: Vector3 = Vector3.ZERO

# Visual facing rotation
var _visual_rotation_start_basis: Basis = Basis.IDENTITY
var _visual_rotation_target_basis: Basis = Basis.IDENTITY
var _visual_rotation_start_time: float = 0.0
var _visual_rotating: bool = false
# The pose the walk/idle heuristic last asked for, latched so it is asked for once per change rather than
# once per frame. Cleared whenever something else takes the pose - a death, or a server posture being
# dropped - so the heuristic re-asserts rather than staying silent on a stale answer.
var _driven_pose: String = ""

# The server's posture, outranked only by death. "IDLE" means it asserts nothing and the local
# walk/idle heuristic owns the pose. Walk and idle never arrive here - sleeping and the like do.
const _POSTURE_NONE = "IDLE"
var _posture: String = _POSTURE_NONE


const _CORRECTION_IGNORE: float = 0.4    # metres of desync trusted as latency, not error
const _CORRECTION_TIME: float = 0.25     # seconds to bleed a correction back in
const _CORRECTION_MAX_BOOST: float = 2.5 # cap on extra metres/sec while catching up
const _SNAP_METRES: float = 2.5          # desync this large just snaps
# How long a walk we started ourselves may run unconfirmed. The server answers a move it accepted within a
# round trip, so an answer that has not come by now means it never will: the path was refused outright (a
# click into the wall beside you), or the request was dropped or rate limited. Fixed rather than derived
# because nothing measures the round trip yet.
const _PREDICTION_GRACE_MSEC: int = 1000
const _ROTATION_DURATION: float = 0.3  # Time to turn the model to face movement direction
const _VISUAL_NODE_NAME = "Visual"

# Ground snapping (see _update_ground_offset).
const _GROUND_MAX_RATE: float = 6.0      # metres/second the correction may travel
const _GROUND_MAX_OFFSET: float = 1.0    # metres of correction the terrain is trusted for

# Where the server says this entity is: whole-voxel coordinates in Godot's axis order, lerped
# between two of them while moving. `position` is this plus the drawing corrections.
var _logical_position: Vector3 = Vector3.ZERO

# The last position the server actually stated, as opposed to the one we interpolated towards it. A
# prediction that is never answered has no stop message to land on - see _abandon_prediction.
var _server_position: Vector3 = Vector3.ZERO

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

	if not _predicted_path.is_empty() and Time.get_ticks_msec() > _prediction_deadline_msec:
		_abandon_prediction()

	if not _is_moving or _nodes.size() < 2 or _speed <= 0.0:
		if pose_is_ours:
			_drive_pose("IDLE")
		return
	else:
		if pose_is_ours:
			_drive_pose("WALK")

	var total := _arc[_arc.size() - 1]

	var step := _speed * delta
	var snapping := false
	if absf(_error) > _SNAP_METRES:
		# Way out of sync (teleport, long stall, dropped packets): jump - backwards too, if that is where
		# the server is. The floor below used to apply here as well, which silently threw away every
		# correction that pointed behind us and left an over-run uncorrected for good.
		step += _error
		_error = 0.0
		snapping = true
	elif absf(_error) > 0.0001:
		# Fold part of the error into this advance, clamped so we never travel backwards.
		var corr := _error * minf(1.0, delta / _CORRECTION_TIME)
		corr = clampf(corr, -_speed * delta, _CORRECTION_MAX_BOOST * delta)
		step += corr
		_error -= corr

	# A correction may stall the walk but never reverse it, because a reversal reads as a stutter. A snap is
	# a different thing and is allowed to go wherever the server says.
	if not snapping:
		step = maxf(step, 0.0)
	_travelled = clampf(_travelled + step, 0.0, total)

	var seg := _segment_at(_travelled)
	var seg_length := _arc[seg + 1] - _arc[seg]
	# A zero-length segment is a duplicate waypoint, which the server tolerates too - draw its start.
	var frac := 0.0 if seg_length <= 0.0 else (_travelled - _arc[seg]) / seg_length
	_logical_position = _nodes[seg].lerp(_nodes[seg + 1], frac)
	_apply_position()

	if seg != _faced_seg:
		_face_direction(_nodes[seg + 1] - _nodes[seg])
		_faced_seg = seg

	# The leftover correction is deliberately dropped rather than waited for: it is under
	# _CORRECTION_IGNORE by now, there is no path left to bleed it along, and holding _is_moving true kept
	# the walk animation running with nothing moving for up to _error/_CORRECTION_MAX_BOOST seconds.
	if _travelled >= total:
		_is_moving = false
		_error = 0.0


## Which segment of [member _nodes] a distance along the chain falls in.
##
## Walks a cursor rather than scanning from zero: this runs once per entity per frame and a path can be
## hundreds of nodes long. The second loop covers the only way [member _travelled] moves backwards, which is
## a negative snap.
func _segment_at(distance: float) -> int:
	var last := _nodes.size() - 2

	while _seg < last and _arc[_seg + 1] <= distance:
		_seg += 1
	while _seg > 0 and _arc[_seg] > distance:
		_seg -= 1

	return _seg


## Ground distance between two logical positions, ignoring height - the same measure MoveSystem charges a
## step, so that a metre here is a metre there.
func _ground_distance(from: Vector3, to: Vector3) -> float:
	return Vector2(to.x - from.x, to.z - from.z).length()


## Asks the visual for a pose, but only when it is not the one already asked for.
func _drive_pose(pose: String) -> void:
	if _driven_pose == pose:
		return

	_driven_pose = pose
	_update_animation_from_client(pose)


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
	_server_position = new_position

	if not _is_moving:
		# Mid-way reports of a walk we predicted and have already finished. They are a trip old, so they
		# describe tiles this entity has left; snapping to one walks it backwards and then forwards again.
		# Only the destination is worth hearing, and the path's removal ends the wait either way.
		if _awaiting_arrival:
			if _ground_distance(new_position, _predicted_destination) <= 0.001:
				_awaiting_arrival = false
			return

		# Nothing to reconcile against, just snap.
		_logical_position = new_position
		_error = 0.0
		_snap_ground_offset()
		return

	var idx := _index_of_node(new_position)
	if idx < 0:
		# Server is off our predicted path (new route/teleport): snap and stop. Whatever we were predicting
		# is settled by this - the server has stated a position that is not on the path at all.
		_logical_position = new_position
		_nodes = [new_position]
		_arc.clear()
		_arc.append(0.0)
		_seg = 0
		_travelled = 0.0
		_is_moving = false
		_is_predicted_walk = false
		_awaiting_arrival = false
		_predicted_path.clear()
		_error = 0.0
		_snap_ground_offset()
		return

	# Reconcile in metres: the server has just reached node idx, which is _arc[idx] along the chain, so the
	# gap to how far we believe we have come is an error we bleed into the movement rate.
	var e := _arc[idx] - _travelled
	if absf(e) <= _CORRECTION_IGNORE:
		# Within latency noise, trust our own prediction.
		return

	# On a walk we started ourselves, being ahead of the server is the whole point: its report of where it
	# is left a one-way trip ago, so it always reads as behind us and correcting for that would stall the
	# walk back into the latency we just removed. Only a shortfall is worth bleeding in. An over-run large
	# enough to matter still falls through to the snap, and a prediction that was simply wrong shows up as
	# an off-path position above rather than as a gap along the path.
	#
	# The exact answer needs the server's report compared against where we were when it was written, which
	# needs a round-trip measurement the protocol does not carry yet.
	if _is_predicted_walk and e < 0.0 and absf(e) <= _SNAP_METRES:
		return

	_error = e


## A walk, or - with an empty path - the end of one. Sent once per walk, not per tile step.
func update_path(msg: PathComponentSMSG) -> void:
	if msg.Path.is_empty():
		_stop_at(msg)
		return

	# Our own click, confirmed. Rebuilding here would zero _travelled and re-anchor the chain on ground we
	# have already covered - a hitch on every click, which is the thing predicting exists to remove.
	#
	# Deliberately not conditional on still being under way: a short click on a slow connection finishes
	# before its own echo arrives, and re-anchoring then would walk the path a second time from the far end
	# of it. There is simply nothing left to adopt in that case.
	if _confirms_prediction(msg.Path):
		if _is_moving:
			_adopt_heights(msg.Path)
		_predicted_path.clear()
		return

	_predicted_path.clear()
	_follow_path(msg.Path, msg.StartOffset)


## Walks a path this client asked for, before the server has confirmed it.
##
## The server stays the authority and will answer: with the same path if it accepted it, with a shorter one
## if it cut the walk at something `path_calculator.gd` walked into - that file ignores terrain, so clicking
## past a rock is routine rather than hostile - and with nothing at all if it refused the first step
## outright. [method update_path] adopts a matching answer without disturbing this walk and re-anchors on
## one that differs; an answer that never comes expires, see [constant _PREDICTION_GRACE_MSEC].
func predict_path(waypoints: Array[Vector3]) -> void:
	_predicted_path = waypoints.duplicate()
	_prediction_deadline_msec = Time.get_ticks_msec() + _PREDICTION_GRACE_MSEC
	_follow_path(waypoints)
	_is_predicted_walk = true
	_awaiting_arrival = true
	_predicted_destination = waypoints[waypoints.size() - 1]


## Whether [param waypoints] is the path we are currently predicting.
##
## Horizontally only, because the vertical is the one thing that legitimately differs: the client
## interpolates it and ignores terrain, and `MoveSystem` replaces every waypoint's height with the ground's
## before echoing the path back. See [method _adopt_heights].
func _confirms_prediction(waypoints: Array[Vector3]) -> bool:
	if _predicted_path.size() != waypoints.size():
		return false

	for i in _predicted_path.size():
		if _ground_distance(_predicted_path[i], waypoints[i]) > 0.001:
			return false

	return true


## Takes the server's heights for a path we are already walking.
##
## Costs no timing: the node chain is paced by [member _arc], which is ground distance and ignores height,
## so replacing the vertical changes where the entity is drawn and nothing else. The anchor at index 0 keeps
## its own height - it is where we are, not a waypoint.
func _adopt_heights(waypoints: Array[Vector3]) -> void:
	for i in waypoints.size():
		_nodes[i + 1] = waypoints[i]


## Gives up on an unconfirmed prediction and returns to where the server last said we were.
func _abandon_prediction() -> void:
	_predicted_path.clear()
	_nodes.clear()
	_arc.clear()
	_seg = 0
	_travelled = 0.0
	_error = 0.0
	_faced_seg = -1
	_is_moving = false
	_is_predicted_walk = false
	_awaiting_arrival = false
	_logical_position = _server_position
	_snap_ground_offset()


## Anchors a chain of waypoints where the entity currently is and starts walking it.
##
## [param start_offset] is the share of the first segment already covered, for a path the server is
## reporting mid-walk; our own click starts from where we stand and passes nothing.
func _follow_path(waypoints: Array[Vector3], start_offset: float = 0.0) -> void:
	# Anchored on the logical position rather than the rendered one: the waypoints behind it are the
	# server's whole voxels, and anchoring the chain on a height that has been nudged onto the terrain
	# would feed the correction into the path and then correct it a second time.
	_nodes.clear()
	_nodes.append(_logical_position)
	for vec3 in waypoints:
		_nodes.append(vec3)

	# Cumulative ground distance, which is what the walk is paced by. The first segment is usually a part
	# tile, because the anchor is wherever we had got to rather than a waypoint - measuring it means it costs
	# the time it deserves instead of a whole step's worth.
	_arc.clear()
	_arc.resize(_nodes.size())
	_arc[0] = 0.0
	for i in range(1, _nodes.size()):
		_arc[i] = _arc[i - 1] + _ground_distance(_nodes[i - 1], _nodes[i])

	_seg = 0

	# Not zero: the server may be telling us about a walk already under way, and _nodes[0] is the tile it
	# last reached rather than where it stands. StartOffset is the share of that first step already walked,
	# so in metres it is that share of the first segment.
	if _nodes.size() >= 2:
		_travelled = clampf(start_offset, 0.0, 1.0) * _arc[1]
	else:
		_travelled = 0.0
	_error = 0.0
	_faced_seg = -1
	_is_moving = _nodes.size() >= 2
	_is_predicted_walk = false
	_awaiting_arrival = false


## The walk is over - finished, or cut short by combat, sleep, death, a stop or a teleport. Only the
## first leaves us where the server is, hence the stop position; snapped, since _error is dropped.
func _stop_at(msg: PathComponentSMSG) -> void:
	_is_moving = false
	_predicted_path.clear()
	_is_predicted_walk = false
	_awaiting_arrival = false
	_travelled = 0.0
	_error = 0.0
	_faced_seg = -1
	_seg = 0

	if msg.HasStopPosition:
		_logical_position = msg.StopPosition
		_snap_ground_offset()   # applies the position too

	_nodes.clear()
	_nodes.append(_logical_position)
	_arc.clear()
	_arc.append(0.0)


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
		_driven_pose = ""

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
		# Nothing to play, but _update_movement has to re-assert its pose over a stale latch.
		_driven_pose = ""
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
