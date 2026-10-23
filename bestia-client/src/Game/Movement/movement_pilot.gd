class_name MovementPilot
extends Node

## Walks the player to something and acts on arrival: a prop to collect, a thing to talk to, a target to hit.
##
## The walk is the server's: one [method ConnectionManager.move_to] to the goal is a journey it plans and walks
## however far. What is left here is noticing arrival, and noticing a walk that got nowhere.
##
## [b]One goal at a time.[/b] Setting a goal is what cancels the one before it, and no two can steer the
## player at once. Switching to another entity cancels it too: the goal was that entity's. See [PilotGoal].

const _GROUP := "movement_pilot"

## How much closer a walk has to bring us to count as progress, in tiles.
##
## Not zero: a walk cut short by the server still moves the player a little, and one that ends against a
## wall would otherwise look like progress forever.
const _PROGRESS_TILES := 1.0

## Walks that achieved nothing before the goal is abandoned.
##
## Two rather than one because a single walk can legitimately stall on a step the server refused, and the
## second identical failure is the one that means a wall.
const _MAX_STALLS := 2

## Seconds to wait after sending a walk before looking at it again.
##
## The gap this covers is the round trip: the entity may not be moving from the moment the walk is sent
## until `PathComponentSMSG` comes back, and without a wait every one of those frames would send another.
const _WALK_COOLDOWN := 0.4

var _goal: PilotGoal = null
var _stalls := 0
var _cooldown := 0.0

## The nearest we have ever been to the goal, in tiles. A walk that fails to beat it walked into
## something - see [constant _MAX_STALLS].
var _closest := INF

## The one in the current scene, or null before [code]Game.tscn[/code] exists, so guard the result.
static func get_instance() -> MovementPilot:
	var loop := Engine.get_main_loop() as SceneTree
	return loop.get_first_node_in_group(_GROUP) as MovementPilot


func _enter_tree() -> void:
	add_to_group(_GROUP)


func _ready() -> void:
	var entity_manager := EntityManager.get_instance()
	if entity_manager != null:
		entity_manager.controlled_entity_changed.connect(func(_previous: int, _now: int) -> void: cancel())


## Collects [param picker], walking to it first if it is out of reach.
func collect(picker: PropPicker) -> void:
	_start(CollectGoal.new(picker))


## Interacts with [param target], walking to it first if it is out of reach. [param entity_id] is the live
## server id of the thing, which the node itself does not always carry.
func interact(target: Node3D, entity_id: int) -> void:
	_start(InteractGoal.new(target, entity_id))


## Swings at [param target], walking to it first if it is out of reach. One message is sent on arrival and the
## server keeps swinging from there, so the goal is done the moment it lands.
func attack(target: Node3D, entity_id: int) -> void:
	_start(AttackGoal.new(target, entity_id))


## Stops steering. Safe to call when idle, which is what lets every manual order call it without
## asking first.
##
## Deliberately does not send a stop: the player's own click is already on its way and would be
## overwritten by one. Cancelling here means "stop steering", not "stand still".
func cancel() -> void:
	_goal = null


func is_steering() -> bool:
	return _goal != null


func _process(delta: float) -> void:
	if _goal == null:
		return

	if not _goal.is_alive():
		cancel()
		return

	var entity := _owned_entity()
	if entity == null or entity.is_dead():
		cancel()
		return

	# Both the walk and the arrival action assert on this - see ConnectionManager.move_to and
	# collect_prop. A socket dropped mid-goal is a frame or two ahead of the scene change.
	if not ConnectionManager.is_ready_to_send():
		return

	var here := _horizontal(entity.get_logical_position())
	var gap := here.distance_to(_goal.destination())

	# Tested before the cast hold below, so a prop already in reach is collected while channelling
	# rather than waiting out a cast that walking would only have cancelled.
	if gap <= _goal.arrival_tiles():
		_arrive()
		return

	# Moving cancels a cast server-side, so ConnectionManager.move_to is swallowed while channelling.
	# Holding is the difference between a walk that waits the cast out and one that counts its own
	# swallowed walks as walking into a wall.
	if entity.is_casting():
		return

	if _cooldown > 0.0:
		_cooldown -= delta
		return

	# The server is still walking it there.
	if entity.is_moving():
		return

	# A walk that got no closer walked into something. Measured against the best we have ever managed
	# rather than against where this walk began, so one that slides along a wall counts as the failure
	# it is.
	if gap > _closest - _PROGRESS_TILES:
		_stalls += 1
		if _stalls >= _MAX_STALLS:
			cancel()
			return
	else:
		_stalls = 0
	_closest = minf(_closest, gap)

	_send_walk(entity)


func _start(goal: PilotGoal) -> void:
	# A walk is only ever sent when the gap is larger than this, so at least one of its axes spans a
	# whole tile. That is what makes PathCalculator's empty-path case unreachable from _send_walk, and it
	# is worth an assert rather than a paragraph.
	assert(goal.arrival_tiles() >= sqrt(2))

	_goal = goal
	_stalls = 0
	_cooldown = 0.0
	_closest = INF


func _arrive() -> void:
	var goal := _goal
	_goal = null
	goal.on_arrival()


## Sends the player walking to the goal, through the ordinary move path so the walk gets the same cast
## check and the same prediction as a click.
##
## Handed over as a world position, because that is what move_to takes: it floors its argument into a tile,
## so a raw tile coordinate would aim up to a whole tile short. The height is the player's own; the server
## puts each step on the ground anyway.
func _send_walk(entity: Entity) -> void:
	_cooldown = _WALK_COOLDOWN
	var goal := _goal.destination()
	var at: Vector3 = entity.get_logical_position()
	ConnectionManager.move_to(TileSpace.tile_centre(Vector3(goal.x, at.y, goal.y)))


## The x and z of a logical position, as the plane everything here reasons in. Godot is Y-up, so the
## northing is z - the same swap [code]MapView.player_metres[/code] performs.
func _horizontal(logical: Vector3) -> Vector2:
	return Vector2(logical.x, logical.z)


func _owned_entity() -> Entity:
	var entity_manager := EntityManager.get_instance()
	if entity_manager == null:
		return null
	var entity = entity_manager.get_controlled_entity()
	return entity if is_instance_valid(entity) else null
