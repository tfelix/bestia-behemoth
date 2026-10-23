class_name Entity extends Node3D

# Movement prediction
# ===================
# Entities follow server-sent paths client-side with the same integrator as the server's MoveSystem.
# Waypoints arrive when a walk starts or changes (PathComponentSMSG; an empty one means "stopped" and says
# where), plus a resync position every MoveSystem.POSITION_RESYNC_STEPS tiles and a speed in tiles/second.
# A change that continues the walk under way is spliced onto it - see _join_index.
#
# `position` is _logical_position plus half a tile horizontally (a tile coordinate names its corner,
# see TileSpace) and _ground_offset vertically. Everything else here works in logical space, so use
# get_logical_position() outside this file - rounding `position` back can land a tile away.


var MasterModelScn = preload("res://Game/Entity/Visual/MasterVisual/MasterVisual.tscn")
var TownsfolkModelScn = preload("res://Game/Entity/Visual/TownsfolkVisual/TownsfolkVisual.tscn")
var StructureVisualScn = preload("res://Game/Entity/Visual/StructureVisual/StructureVisual.tscn")
var Camera = preload("res://Game/SpringArmCamera/SpringArmCamera.tscn")

var entity_id: int = 0

# Species id of this entity's body, 0 for anything that is not a bestia. Kept because disposition is
# decided from it - see EntityManager.is_entity_friendly.
var _bestia_id: int = 0

# True once a TownsfolkVisual has arrived. Townsfolk carry no species id, so this is what answers
# is_non_combatant for them - see there.
var _is_townsfolk: bool = false

# True for a body that can be fought: a bestia, a townsperson or a master. A ward stone, an item or an effect
# is none of these - see EntityManager.can_be_harmed.
var _is_creature: bool = false

# This entity's own name, for anything that labels it. Empty for a mob, which has only a species.
var _display_name: String = ""

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

# What the attributes, level and gear add up to in a fight, resolved by the server (owner only): {"atk": int,
# "weapon_atk": int, "matk", "weapon_matk", "hit", "flee", "crit", "soft_defense", "hard_defense", "aspd"}.
# Empty until the first push.
var _combat_stats: Dictionary = {}

# Status points (masters only).
var _status_points: int = 0

# Condition pools, null until the first push - a master spawns full, so that may be its only one.
var _health: ConditionPool = null
var _mana: ConditionPool = null
var _stamina: ConditionPool = null

# The last health, worn gear and guild tag pushes, replayed into a visual that was built after they arrived - see
# _seed_visual. The guild tag is null while the entity's master is in no guild.
var _last_health_msg: HealthComponentSMSG = null
var _last_worn_gear_msg: WornGearComponentSMSG = null
var _guild_tag: GuildTagComponentSMSG = null

# True while lying dead awaiting respawn (player bodies only; mobs vanish). Gates the walk/idle pose.
var _is_dead: bool = false

# What decides whether a death bleeds, see _bleed. A body that arrives already dead is in its column's ground
# snapshot, so only one seen alive for a frame first adds blood of its own.
var _has_position: bool = false      # the server has said where this is, as opposed to the origin it starts at
var _seen_alive: bool = false
var _bled: bool = false

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
# Highest index of _nodes whose tile the ground has been told about, see _emit_footfalls. Node 0 is the anchor the
# walk was joined at, not a step, so it starts there.
var _footfall_node: int = 0

# Visual facing rotation, held as quaternions rather than as bases. The visual carries a scale of its
# own - a child townsperson is drawn at 0.72 - and interpolating whole bases drags that scale along:
# Basis.slerp lerps the row lengths towards the target's, which is a bare looking_at and so always 1.
# A child therefore grew to adult size over its first few tiles of walking, and every frame of that
# fed Godot a basis that is not a rotation, which it refuses to read a quaternion out of.
var _visual_rotation_start: Quaternion = Quaternion.IDENTITY
var _visual_rotation_target: Quaternion = Quaternion.IDENTITY
# A walk this client started on its own click, still waiting for the server to confirm it. Empty on every
# entity but our own, and cleared the moment the server answers. See predict_path.
var _predicted_path: Array[Vector3] = []
var _prediction_deadline_msec: int = 0
# Whether that walk set off from standing rather than continuing one, which decides what a position push means
# while it waits - see update_position.
var _predicted_from_standing: bool = false

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
# Shortest an unconfirmed prediction may be given, whatever the measured round trip says. A local server
# reports well under a millisecond, and letting the grace follow it down would abandon a good prediction
# over one hitched frame.
const _PREDICTION_GRACE_FLOOR_MSEC: int = 600
const _ROTATION_DURATION: float = 0.3  # Time to turn the model to face movement direction
const _VISUAL_NODE_NAME = "Visual"
const _WARDED_EFFECT_ID: int = 10  # the server's StatusEffectId.WARDED

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

## Kind and id of the body built by [method update_visual], so a repeated snapshot can leave it standing.
var _visual_key: String = ""

const _HELD_GROUND_CHECK_SECONDS: float = 0.25
var _since_held_ground_check: float = _HELD_GROUND_CHECK_SECONDS


func _process(delta: float) -> void:
	_update_movement(delta)
	_update_visual_rotation()
	_update_ground_offset(delta)
	_update_visible_on_held_ground(delta)

	# After a whole frame, so everything an appear snapshot says about a body has been applied by now.
	if _has_position and not _is_dead:
		_seen_alive = true


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

	_emit_footfalls(_nodes.size() - 1 if _travelled >= total else seg)

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


## Tells the ground about every tile node the walk has reached since the last call, one footfall each, so it can
## wear and take a print. Reads the walk and never changes it.
##
## Every node crossed in a frame counts, a forward snap included, because the server stepped on each of them. A
## backward snap reports nothing and leaves the cursor where it was, so no tile is reported twice.
func _emit_footfalls(reached: int) -> void:
	# Our own click is a guess until the server answers: path_calculator.gd ignores terrain, so a print could land
	# in a column the server never touches and nothing would correct it. Confirmation releases them all at once.
	if reached <= _footfall_node or not _predicted_path.is_empty():
		return

	for node in range(_footfall_node + 1, reached + 1):
		_report_footfall(node)

	_footfall_node = reached


## One footfall onto [member _nodes] [param node], from the node before it.
func _report_footfall(node: int) -> void:
	if ConnectionManager.chunk_stream == null:
		return

	# The anchor is wherever the walk was joined, which is between tiles when that was mid-step.
	var stepped_from: Vector3 = _nodes[node - 1].round() if node == 1 else _nodes[node - 1]
	var stepped_to: Vector3 = _nodes[node]

	ConnectionManager.chunk_stream.RecordFootfall(
		entity_id, stepped_from.x, stepped_from.z, stepped_to.x, stepped_to.z, stepped_to.y)


## Ground distance between two logical positions, ignoring height - the same measure MoveSystem charges a
## step, so that a metre here is a metre there.
func _ground_distance(from: Vector3, to: Vector3) -> float:
	return Vector2(to.x - from.x, to.z - from.z).length()


## Asks the visual for a pose, but only when it is not the one already asked for.
func _drive_pose(pose: String) -> void:
	if _driven_pose == pose:
		return

	# An entity can idle or walk a frame before its visual arrives. Latching the pose now would leave the
	# visual never hearing it, so wait for the visual instead of reporting it missing.
	if get_node_or_null(_VISUAL_NODE_NAME) == null:
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


## The player's own entities are still sent beyond the streamed terrain, so the map can show them. There the body
## would float over empty ground, and its billboards would draw through nearer hills.
func _update_visible_on_held_ground(delta: float) -> void:
	_since_held_ground_check += delta
	if _since_held_ground_check < _HELD_GROUND_CHECK_SECONDS:
		return
	_since_held_ground_check = 0.0

	var stream = ConnectionManager.chunk_stream
	if stream == null or _camera != null:
		visible = true
		return

	visible = stream.HoldsGroundAt(
		_logical_position.x + TileSpace.CENTRE_OFFSET.x,
		_logical_position.z + TileSpace.CENTRE_OFFSET.z)


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

	var visual = get_node_or_null(_VISUAL_NODE_NAME) as Node3D
	if visual == null:
		return

	# use_model_front = true since the model's forward axis is +Z, not Godot's default -Z.
	_visual_rotation_start = visual.quaternion
	_visual_rotation_target = Basis.looking_at(flat_direction, Vector3.UP, true).get_rotation_quaternion()
	_visual_rotation_start_time = Time.get_ticks_msec() / 1000.0
	_visual_rotating = true


func _update_visual_rotation() -> void:
	if not _visual_rotating:
		return

	var visual = get_node_or_null(_VISUAL_NODE_NAME) as Node3D
	if visual == null:
		_visual_rotating = false
		return

	var current_time = Time.get_ticks_msec() / 1000.0
	var progress = (current_time - _visual_rotation_start_time) / _ROTATION_DURATION

	# Node3D.quaternion writes the rotation and leaves the scale alone, which is the whole point.
	if progress >= 1.0:
		visual.quaternion = _visual_rotation_target
		_visual_rotating = false
	else:
		visual.quaternion = _visual_rotation_start.slerp(_visual_rotation_target, _ease_out_cubic(progress))


func show_chat(msg: ChatSMSG) -> void:
	var visual = _get_visual_for_method("show_chat")
	if visual != null:
		visual.show_chat(msg)
	else:
		printerr("Entity %s has no show_chat visual", [entity_id])


## Builds the visual from a kind plus a catalogue id. Masters go through update_master_visual.
func update_visual(msg: VisualComponentSMSG) -> void:
	_bestia_id = msg.VisualId if msg.Kind == VisualKind.BESTIA else 0
	_is_creature = msg.Kind == VisualKind.BESTIA

	# An owned entity coming back into view is snapshotted again; rebuilding would replay its fade-in.
	var key := "%s:%s" % [msg.Kind, msg.VisualId]
	if key == _visual_key and has_node(_VISUAL_NODE_NAME):
		return

	var scene: PackedScene = _visual_scene_for(msg)
	if scene == null:
		return

	_release_visual()
	_visual_key = key

	# Untyped on purpose: each kind's visual declares its own setup_visual, taking its own message.
	var visual = scene.instantiate()
	visual.setup_visual(msg)
	visual.name = _VISUAL_NODE_NAME
	add_child(visual)
	_seed_visual(visual)


## True when nothing may damage this entity - a townsperson. Anything that is not a bestia answers
## false: a master is not a non-combatant, it is simply not one of these.
##
## Townsfolk are answered from the flag rather than the bestia DB because they send no species id at
## all. Only a UI hint either way - the server enforces invulnerability - so being wrong here costs a
## cursor, not a rule.
func is_non_combatant() -> bool:
	if _is_townsfolk:
		return true

	var bestia := BestiaDB.get_instance().get_bestia(_bestia_id)
	return bestia != null and bestia.non_combatant


func is_creature() -> bool:
	return _is_creature


## Whether a ward stone keeps other players from harming this entity right now.
func is_warded() -> bool:
	return _effects.any(func(effect): return effect.EffectId == _WARDED_EFFECT_ID)


func _visual_scene_for(msg: VisualComponentSMSG) -> PackedScene:
	match msg.Kind:
		VisualKind.BESTIA:
			var bestia_resource = BestiaDB.get_instance().get_bestia(msg.VisualId)
			# An unknown id is a real desync; a known species without a body gets the placeholder instead.
			if bestia_resource == null:
				printerr("Entity %s: no bestia %s in the BestiaDB" % [entity_id, msg.VisualId])
				return null
			return bestia_resource.get_bestia_visual()
		VisualKind.ITEM:
			var item_resource = ItemDB.get_instance().get_item(msg.VisualId)
			# An unknown id is a real desync; a known item without a mesh gets a placeholder instead.
			if item_resource == null:
				printerr("Entity %s: no item %s in the ItemDB" % [entity_id, msg.VisualId])
				return null
			return item_resource.get_item_visual()
		VisualKind.STRUCTURE:
			# One scene for every structure, unlike the two above: the kind picks a mesh inside it rather
			# than a scene here, because the art comes from PropAppearance rather than a per-kind .tscn.
			return StructureVisualScn
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
	_display_name = msg.Name
	_is_creature = true

	_release_visual()
	var visual = MasterModelScn.instantiate() as MasterVisual
	visual.setup_visual(msg)
	visual.name = _VISUAL_NODE_NAME
	add_child(visual)
	_seed_visual(visual)


## Townsfolk arrive here instead of through update_visual: one commoner archetype stands in for a whole
## town, so a species id could only say "a townsperson" - which is what they all used to be labelled.
func update_townsfolk_visual(msg: TownsfolkVisualComponentSMSG) -> void:
	_is_townsfolk = true
	_is_creature = true
	_display_name = msg.Name

	_release_visual()
	var visual = TownsfolkModelScn.instantiate() as TownsfolkVisual
	visual.setup_townsfolk(msg)
	visual.name = _VISUAL_NODE_NAME
	add_child(visual)
	_seed_visual(visual)


## Empty for anything the server has not named individually, which is every mob.
func get_display_name() -> String:
	return _display_name


## Frees the attached visual, giving up its name first.
##
## queue_free is deferred to the end of the frame, so a replacement added in the same frame would find the
## name still taken - Godot renames the newcomer, and _VISUAL_NODE_NAME then resolves to the node that is
## about to disappear, and to null after that.
func _release_visual() -> void:
	_visual_key = ""
	var existing = get_node_or_null(_VISUAL_NODE_NAME)
	if existing == null:
		return

	existing.name = "VisualOutgoing"
	existing.queue_free()


## Pushes state that arrived before this visual existed into it. Only health needs it so far.
func _seed_visual(visual: Node) -> void:
	if _last_health_msg != null and visual.has_method("update_health"):
		visual.update_health(_last_health_msg)
	if _last_worn_gear_msg != null and visual.has_method("update_worn_gear"):
		visual.update_worn_gear(_last_worn_gear_msg)
	if _guild_tag != null and visual.has_method("set_guild_tag"):
		visual.set_guild_tag(_guild_tag)


func set_selected(is_selected: bool) -> void:
	print("Entity: set_selected: %s" % [is_selected])


## Called by mouse_manager when this entity is clicked; may delegate to the visual.
func on_interact() -> void:
	print("Entity: on_interact")


func update_position(msg: PositionComponent) -> void:
	var new_position: Vector3 = msg.Position
	_server_position = new_position
	_has_position = true

	# A walk set off on from standing is answered by its own path or, refused, by this push and nothing else:
	# a standing entity sends no resyncs. So this is the refusal. Waiting out the prediction's deadline instead
	# walked on into the slope or wall that refused it, and snapped back from there - on every click at it.
	if _predicted_from_standing and not _predicted_path.is_empty():
		_abandon_prediction()
		return

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
		_footfall_node = 0
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


## A walk, or - with an empty path - the end of one. Sent when a walk starts and whenever the server changes it
## (a leg appended, a route picked mid-walk), not per tile step.
func update_path(msg: PathComponentSMSG) -> void:
	if msg.Path.is_empty():
		_stop_at(msg)
		return

	# The walk under way, continued: our own click confirmed, a leg appended, a route picked mid-walk. The server
	# is a trip behind us on it, so rebuilding here would re-anchor the chain where we stand and walk back to the
	# tile it is stepping onto - a hitch on every click and every leg, which is the thing predicting exists to
	# remove.
	#
	# Deliberately not conditional on still being under way: a short click on a slow connection finishes
	# before its own echo arrives, and re-anchoring then would walk the path a second time from the far end
	# of it. There is simply nothing left to walk in that case.
	var at := _join_index(msg.Path)
	if at >= 0:
		# While our own order is unanswered, a path that contradicts it answers an earlier one it overtook.
		if not _predicted_path.is_empty() and not _agrees(msg.Path, at):
			return

		_predicted_path.clear()
		_continue_from(_joining_node(), msg.Path, at)
		if _awaiting_arrival:
			_predicted_destination = _nodes[_nodes.size() - 1]

		# A walk that finished while it waited has no frames left to report its footfalls in.
		if not _is_moving:
			_emit_footfalls(_nodes.size() - 1)
		return

	_predicted_path.clear()
	_follow_path(msg.Path, msg.StartOffset)


## Walks a path this client asked for, before the server has confirmed it.
##
## The server stays the authority and will answer: with the same path if it accepted it, with a shorter one
## if it cut the walk at something `path_calculator.gd` walked into - that file ignores terrain, so clicking
## past a rock is routine rather than hostile - and with nothing at all if it refused the first step
## outright. [method update_path] adopts an answer that continues this walk without disturbing it; an answer
## that never comes expires, see [method _prediction_grace_msec].
##
## A path that starts on [method joining_tile] continues the walk under way from there, as the server does.
func predict_path(waypoints: Array[Vector3]) -> void:
	_predicted_path = waypoints.duplicate()
	_prediction_deadline_msec = Time.get_ticks_msec() + _prediction_grace_msec()

	var node := _joining_node()
	_predicted_from_standing = node < 0 or not _same_column(_nodes[node], waypoints[0])
	if _predicted_from_standing:
		_follow_path(waypoints)
	else:
		_continue_from(node, waypoints, 0)

	_is_predicted_walk = true
	_awaiting_arrival = true
	_predicted_destination = waypoints[waypoints.size() - 1]


## Where a route picked now has to start for the server to join it onto the walk under way - the tile this entity
## is stepping onto - or [constant Vector3.INF] when there is no walk, and a route starts where it stands.
##
## A route drawn from the nearest tile instead got clicks refused mid-walk: by the middle of every step that tile
## is two from the last one the server reached, which is all it measures from. See MoveActiveEntityHandler.
func joining_tile() -> Vector3:
	var node := _joining_node()
	return Vector3.INF if node < 0 else _nodes[node]


## The node of [member _nodes] a route continues from: the one being walked towards, or the last of a walk that
## finished here while the server, a trip behind, may still be on it. -1 when there is no walk to continue.
##
## Measured on [member _travelled] rather than [member _is_moving], which a speed of zero also clears mid-step.
func _joining_node() -> int:
	if _nodes.size() < 2:
		return -1
	if _travelled < _arc[_arc.size() - 1]:
		return _seg + 1
	return _nodes.size() - 1


## Where [param waypoints], the server's account of a walk, reaches the node we continue from (see
## [method _joining_node]), or -1 when it is not the walk under way.
##
## It is when it runs through that tile and, up to there, retraces the tiles we walked to get there: the server
## is behind us on the same walk, and everything after that tile is what it has decided since.
func _join_index(waypoints: Array[Vector3]) -> int:
	var node := _joining_node()
	if node < 0:
		return -1

	for at in waypoints.size():
		if _same_column(waypoints[at], _nodes[node]) and _retraces(waypoints, at, node):
			return at

	return -1


## Whether [param waypoints] up to [param at] are the walk's own tiles up to [param node], read backwards. Node 0
## is not one of them - it is wherever the walk was joined - so a path that began before it matches as far as the
## walk goes back.
func _retraces(waypoints: Array[Vector3], at: int, node: int) -> bool:
	for back in range(1, at + 1):
		if node - back < 1:
			return true
		if not _same_column(waypoints[at - back], _nodes[node - back]):
			return false

	return true


## Whether [param waypoints] from [param at] go where our walk goes from the node it joins at, for as far as both
## go. A walk the server cut short at a wall agrees; one heading somewhere else does not.
func _agrees(waypoints: Array[Vector3], at: int) -> bool:
	var node := _joining_node()
	for i in mini(waypoints.size() - at, _nodes.size() - node):
		if not _same_column(waypoints[at + i], _nodes[node + i]):
			return false

	return true


## Keeps the walk up to [param node] and goes on along [param waypoints] after [param at], the waypoint on the same
## tile. What has been walked stays walked: [member _travelled] and the cursor are left alone.
func _continue_from(node: int, waypoints: Array[Vector3], at: int) -> void:
	# The server's heights for the tiles both have, which footfalls are reported on rather than the ones
	# path_calculator.gd interpolated. The columns already agree, so no distance along the chain changes.
	for back in range(0, at + 1):
		if node - back < 1:
			break
		_nodes[node - back] = waypoints[at - back]

	_nodes.resize(node + 1)
	_arc.resize(node + 1)
	for i in range(at + 1, waypoints.size()):
		var from: Vector3 = _nodes[_nodes.size() - 1]
		_nodes.append(waypoints[i])
		_arc.append(_arc[_arc.size() - 1] + _ground_distance(from, waypoints[i]))

	# A walk that had finished resumes, one that is cut short at the tile ahead still reaches it.
	_is_moving = _travelled < _arc[_arc.size() - 1]


## Whether two logical positions are on the same tile. Horizontal, because the height is the one thing the server
## changes - it replaces every waypoint's with the ground's, see Path.resolveGround.
func _same_column(a: Vector3, b: Vector3) -> bool:
	return _ground_distance(a, b) <= 0.001


## How long to keep walking a path the server has not answered for.
##
## Long enough that a server which was going to answer has answered, and no longer: past that the walk is
## running on nothing. Two round trips plus a tick's worth of slack, off the measured latency rather than a
## guess - which is what measuring it is for.
func _prediction_grace_msec() -> int:
	if ConnectionManager.latency_msec < 0.0:
		return _PREDICTION_GRACE_FLOOR_MSEC

	# Annotated rather than inferred: connection_manager.gd has no class_name, so the analyser sees an
	# autoload member as Variant and cannot infer a type through the arithmetic.
	var budget: float = 2.0 * (ConnectionManager.latency_msec + ConnectionManager.latency_jitter_msec) + 100.0

	return maxi(_PREDICTION_GRACE_FLOOR_MSEC, int(budget))


## Gives up on an unconfirmed prediction and returns to where the server last said we were.
func _abandon_prediction() -> void:
	_predicted_path.clear()
	_nodes.clear()
	_arc.clear()
	_seg = 0
	_footfall_node = 0
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
	_footfall_node = 0

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
	_footfall_node = 0

	if msg.HasStopPosition:
		_has_position = true
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

		# Only a fall that was watched. A body that arrives dead is already part of its column's blood.
		if _seen_alive:
			_bleed()
	else:
		# Back on its feet, so the next death is a new one.
		_bled = false

	var visual = get_node_or_null(_VISUAL_NODE_NAME)
	if visual != null and visual.has_method("set_dead"):
		visual.set_dead(_is_dead)


## Whether this entity is a body waiting to respawn.
func is_dead() -> bool:
	return _is_dead


## Soaks the blood of this entity's death into the ground under it, once per death.
##
## Only a creature bleeds - props and structures also vanish on death - and only where it was really seen: an
## entity made for an id the server never announced sits at the origin.
func _bleed() -> void:
	if _bled or not _is_creature or not _has_position or ConnectionManager.chunk_stream == null:
		return

	_bled = true

	var tile := _logical_position.round()
	ConnectionManager.chunk_stream.RecordDeath(tile.x, tile.z)


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


## Progress on a construction site. Its removal - finished, or knocked down - arrives the same way, and
## needs nothing here: the entity is destroyed either way, so the visual goes with it.
func update_construction(msg: ConstructionComponentSMSG) -> void:
	var visual = _get_visual_for_method("update_construction")
	if visual != null:
		visual.update_construction(msg)


## Movement clicks are suppressed while this is true - see ConnectionManager.move_to.
func is_casting() -> bool:
	return _casting


## Whether the predicted walk is still running. Good for spotting one that stopped short, no good
## for detecting arrival - measure the distance for that.
func is_moving() -> bool:
	return _is_moving


## The last tile of the walk, or where the entity stands when it is not walking.
func path_end() -> Vector3:
	return _nodes[-1] if _is_moving else _logical_position


## Tile steps left until the predicted walk ends.
func remaining_path_steps() -> float:
	return _arc[_arc.size() - 1] - _travelled if _is_moving else 0.0


## Tile steps per second.
func speed() -> float:
	return _speed


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
			"upgrade_level": int(equipped.UpgradeLevel),
			"durability": int(equipped.Durability),
			"max_durability": int(equipped.MaxDurability),
			"slots": int(equipped.Slots),
			"item_level": int(equipped.ItemLevel),
			"rolled_stats": Array(equipped.RolledStats),
		}
	_equipment = by_slot


func get_equipment() -> Dictionary:
	return _equipment


func update_worn_gear(msg: WornGearComponentSMSG) -> void:
	_last_worn_gear_msg = msg
	var visual = _get_visual_for_method("update_worn_gear")
	if visual != null:
		visual.update_worn_gear(msg)


func update_guild_tag(msg: GuildTagComponentSMSG) -> void:
	_guild_tag = null if msg.Removed else msg
	var visual = _get_visual_for_method("set_guild_tag")
	if visual != null:
		visual.set_guild_tag(_guild_tag)


func get_guild_tag() -> GuildTagComponentSMSG:
	return _guild_tag


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


## The wire message as the dictionary this entity caches. Static so the status window, which is handed the
## message straight off the socket, reads the very same keys.
static func combat_stats_of(msg: CombatStatsComponentSMSG) -> Dictionary:
	return {
		"atk": msg.Atk,
		"weapon_atk": msg.WeaponAtk,
		"matk": msg.Matk,
		"weapon_matk": msg.WeaponMatk,
		"hit": msg.Hit,
		"flee": msg.Flee,
		"crit": msg.Crit,
		"soft_defense": msg.SoftDefense,
		"hard_defense": msg.HardDefense,
		"aspd": msg.Aspd,
	}


func update_combat_stats(msg: CombatStatsComponentSMSG) -> void:
	_combat_stats = combat_stats_of(msg)


func get_combat_stats() -> Dictionary:
	return _combat_stats


func update_status_points(msg: StatusPointsComponentSMSG) -> void:
	_status_points = msg.Points


func get_status_points() -> int:
	return _status_points


## Safe to repeat: EntityManager re-asks whenever an entity arrives.
func select_for_active() -> void:
	if _camera != null:
		return
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
	# A body already dead has either bled when it fell or arrived in its column's blood. Only a mob dying as it
	# vanishes still has a death to add.
	if msg.IsDead() and not _is_dead:
		_bleed()

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
