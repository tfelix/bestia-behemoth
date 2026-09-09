class_name MovementPilot
extends Node

## Walks the player to a goal, one leg at a time.
##
## [b]Entirely client-side, and that is the design.[/b] The server has no notion of a destination: it
## takes a path of adjacent steps, validates it and walks it (`MoveActiveEntityHandler`). So a long
## walk is not a message - it is this, sending the next short stretch each time the last one runs
## out, until the player arrives, gets stuck, or takes over.
##
## [b]Why legs and not one long path.[/b] [PathCalculator] says outright that it ignores terrain, and
## the server cuts a path at the first step it cannot walk. A kilometre of straight line sent in one
## message would therefore be truncated at the first hillside and the player would stop dead with no
## idea why. A short leg, re-aimed from wherever they actually got to, walks round obstacles by
## repetition instead - each leg starts from the truth of where the server put them.
##
## [b]One goal at a time.[/b] Walking to a map pin and walking to a prop are the same act, so they
## are one mechanism: setting a goal is what cancels the one before it, and no two can steer the
## player at once. See [PilotGoal].

const _GROUP := "movement_pilot"

## Tiles per leg.
##
## Long enough that the round trip per leg is not the dominant cost of the walk, short enough that a
## leg truncated against terrain has not wasted much - and it is deliberately near the server's own
## `NavigationConfig.localSearchSpan` of 32, which is one chunk and the distance that side considers
## "nearby".
const _LEG_TILES := 24.0

## How much closer a leg has to bring us to count as progress, in tiles.
##
## Not zero: a leg cut short by the server still moves the player a little, and a walk that ends
## against a wall would otherwise look like progress forever.
const _PROGRESS_TILES := 1.0

## Legs that achieved nothing before the goal is abandoned.
##
## Two rather than one because a single leg can legitimately stall - the path crosses a chunk the
## server has not finished tracking, and `MoveActiveEntityHandler` says it treats an unvouched-for
## step as walkable rather than blocked. The second identical failure is the one that means a wall.
const _MAX_STALLS := 2

## Seconds to wait after sending a leg before considering the next one.
##
## The gap this covers is the round trip: the entity is not moving from the moment the leg is sent
## until `PathComponentSMSG` comes back, and without a wait every one of those frames would send
## another leg.
const _LEG_COOLDOWN := 0.4

var _goal: PilotGoal = null
var _stalls := 0
var _cooldown := 0.0

## The nearest we have ever been to the goal, in tiles. A leg that fails to beat it walked into
## something - see [constant _MAX_STALLS].
var _closest := INF


## The one in the current scene, or null before [code]Game.tscn[/code] exists, so guard the result.
static func get_instance() -> MovementPilot:
	var loop := Engine.get_main_loop() as SceneTree
	return loop.get_first_node_in_group(_GROUP) as MovementPilot


func _enter_tree() -> void:
	add_to_group(_GROUP)


## Sets off for [param tile_xz] - the destination in tile coordinates, x and z, which is what the
## entity's own logical position is in.
##
## No height: [PathCalculator] interpolates one and the server overwrites it from the heightfield
## anyway, so a caller that has only a map position is not being asked to invent one.
func travel_to(tile_xz: Vector2) -> void:
	_start(TravelGoal.new(tile_xz))


## Collects [param picker], walking to it first if it is out of reach.
func collect(picker: PropPicker) -> void:
	_start(CollectGoal.new(picker))


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

	# Both the leg and the arrival action assert on this - see ConnectionManager.move_to and
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
	# swallowed legs as walking into a wall.
	if entity.is_casting():
		return

	if _cooldown > 0.0:
		_cooldown -= delta
		return

	# The leg is still being walked. Nothing to decide until it runs out.
	if entity.is_moving():
		return

	# A leg that got no closer is a leg that walked into something. Measured against the best we have
	# ever managed rather than against where this leg began, so a leg that slides along a wall counts
	# as the failure it is.
	if gap > _closest - _PROGRESS_TILES:
		_stalls += 1
		if _stalls >= _MAX_STALLS:
			cancel()
			return
	else:
		_stalls = 0
	_closest = minf(_closest, gap)

	_send_leg(entity, here)


func _start(goal: PilotGoal) -> void:
	# A leg is only ever sent when the gap is larger than this, so the leg vector is at least this
	# long, so at least one of its axes spans a whole tile. That is what makes PathCalculator's
	# empty-path case unreachable from _send_leg, and it is worth an assert rather than a paragraph.
	assert(goal.arrival_tiles() >= sqrt(2))

	_goal = goal
	_stalls = 0
	_cooldown = 0.0
	_closest = INF


func _arrive() -> void:
	var goal := _goal
	_goal = null
	goal.on_arrival()


## Aims the next leg at a point along the line to the goal, and hands it to the ordinary move path.
func _send_leg(entity: Entity, here: Vector2) -> void:
	var to_goal := _goal.destination() - here
	var target := here + to_goal.limit_length(_LEG_TILES)
	_cooldown = _LEG_COOLDOWN

	# Through ConnectionManager.move_to like any other walk, so the leg gets the same cast check, the
	# same PathCalculator and the same message.
	#
	# Handed over as a world position, because that is what move_to takes: it floors its argument
	# into a tile, so a raw tile coordinate would aim every leg up to a whole tile short. The height
	# is the player's own - PathCalculator lerps between the endpoints, so giving it the one they are
	# standing at keeps the invented vertical flat instead of sloping towards a guess.
	var at: Vector3 = entity.get_logical_position()
	ConnectionManager.move_to(TileSpace.tile_centre(Vector3(target.x, at.y, target.y)))


## The x and z of a logical position, as the plane everything here reasons in. Godot is Y-up, so the
## northing is z - the same swap [code]MapView.player_metres[/code] performs.
func _horizontal(logical: Vector3) -> Vector2:
	return Vector2(logical.x, logical.z)


func _owned_entity() -> Entity:
	var entity_manager := EntityManager.get_instance()
	if entity_manager == null:
		return null
	var entity = entity_manager.get_owned_entity()
	return entity if is_instance_valid(entity) else null
