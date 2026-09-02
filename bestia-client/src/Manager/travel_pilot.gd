extends Node

## Walks the player somewhere far off, one leg at a time.
##
## [b]Entirely client-side, and that is the design.[/b] The server has no notion of a destination: it takes a
## path of adjacent steps, validates it and walks it (`MoveActiveEntityHandler`). So a long walk is not a
## message - it is this, sending the next short stretch each time the last one runs out, until the player
## arrives, gets stuck, or takes over.
##
## [b]Why legs and not one long path.[/b] [PathCalculator] says outright that it ignores terrain, and the
## server cuts a path at the first step it cannot walk. A kilometre of straight line sent in one message would
## therefore be truncated at the first hillside and the player would stop dead with no idea why. A short leg,
## re-aimed from wherever they actually got to, walks round obstacles by repetition instead - each leg starts
## from the truth of where the server put them.
##
## An autoload beside [code]MouseManager[/code] because both ends of it are global: the map starts a journey
## and the mouse layer cancels one, and neither should have to find the other in the scene tree.

## The journey ended. [param arrived] is false when it was cancelled or gave up.
signal travel_ended(arrived: bool)

## Tiles per leg.
##
## Long enough that the round trip per leg is not the dominant cost of the walk, short enough that a leg
## truncated against terrain has not wasted much - and it is deliberately near the server's own
## `NavigationConfig.localSearchSpan` of 32, which is one chunk and the distance that side considers
## "nearby".
const _LEG_TILES := 24.0

## How near the destination counts as arrived, in tiles. A tile is a metre, and insisting on the exact one
## would leave the player shuffling on the spot when the server's ground correction disagrees by half of one.
const _ARRIVAL_TILES := 1.5

## How far a leg has to move the player to count as progress, in tiles.
##
## Not zero: a leg cut short by the server still moves them a little, and a walk that ends against a wall
## would otherwise look like progress forever.
const _PROGRESS_TILES := 1.0

## Legs that achieved nothing before the journey is abandoned.
##
## Two rather than one because a single leg can legitimately stall - the path crosses a chunk the server has
## not finished tracking, and `MoveActiveEntityHandler` says it treats an unvouched-for step as walkable
## rather than blocked. The second identical failure is the one that means a wall.
const _MAX_STALLS := 2

## Seconds to wait after sending a leg before considering the next one.
##
## The gap this covers is the round trip: the entity is not moving from the moment the leg is sent until
## `PathComponentSMSG` comes back, and without a wait every one of those frames would send another leg.
const _LEG_COOLDOWN := 0.4

var _travelling := false
var _destination := Vector2.ZERO

## Where the current leg started, to tell progress from a walk into a wall.
var _leg_started_at := Vector2.ZERO
var _stalls := 0
var _cooldown := 0.0


## Sets off for [param tile_xz] - the destination in tile coordinates, x and z, which is what the entity's
## own logical position is in.
##
## No height: [PathCalculator] interpolates one and the server overwrites it from the heightfield anyway, so
## a caller that has only a map position is not being asked to invent one.
func travel_to(tile_xz: Vector2) -> void:
	var entity := _owned_entity()
	if entity == null:
		return

	_destination = tile_xz
	_travelling = true
	_stalls = 0
	_cooldown = 0.0
	_leg_started_at = _horizontal(entity.get_logical_position())


## Stops travelling. Safe to call when not travelling, which is what lets the mouse layer call it on every
## manual move without asking first.
##
## Deliberately does not send a stop: the player's own click is already on its way and would be overwritten
## by one. Cancelling here means "stop steering", not "stand still".
func cancel() -> void:
	if not _travelling:
		return

	_travelling = false
	travel_ended.emit(false)


func is_travelling() -> bool:
	return _travelling


func _process(delta: float) -> void:
	if not _travelling:
		return

	var entity := _owned_entity()
	if entity == null or entity.is_dead():
		_finish(false)
		return

	if _cooldown > 0.0:
		_cooldown -= delta
		return

	# The leg is still being walked. Nothing to decide until it runs out.
	if entity.is_moving():
		return

	var here := _horizontal(entity.get_logical_position())

	if here.distance_to(_destination) <= _ARRIVAL_TILES:
		_finish(true)
		return

	# A leg that ended where it began is a leg that walked into something. See _MAX_STALLS.
	if here.distance_to(_leg_started_at) < _PROGRESS_TILES:
		_stalls += 1
		if _stalls >= _MAX_STALLS:
			_finish(false)
			return
	else:
		_stalls = 0

	_send_leg(entity, here)


## Aims the next leg at a point along the line to the destination, and hands it to the ordinary move path.
func _send_leg(entity: Node, here: Vector2) -> void:
	var to_goal := _destination - here
	var target := here + to_goal.limit_length(_LEG_TILES)

	# The tile the leg would end on is the one the player is already standing on, so there is no path to send
	# and no point asking again next frame. Close enough to be arrival in every sense that matters.
	if floorf(target.x) == floorf(here.x) and floorf(target.y) == floorf(here.y):
		_finish(true)
		return

	_leg_started_at = here
	_cooldown = _LEG_COOLDOWN

	# Through ConnectionManager.move_to like any other walk, so the leg gets the same cast check, the same
	# PathCalculator and the same message. The height is the player's own: PathCalculator lerps between the
	# endpoints, so giving it the one they are standing at keeps the invented vertical flat instead of
	# sloping towards a guess.
	var at: Vector3 = entity.get_logical_position()
	ConnectionManager.move_to(Vector3(target.x, at.y, target.y))


func _finish(arrived: bool) -> void:
	_travelling = false
	travel_ended.emit(arrived)


## The x and z of a logical position, as the plane everything here reasons in. Godot is Y-up, so the
## northing is z - the same swap [code]MapView.player_metres[/code] performs.
func _horizontal(logical: Vector3) -> Vector2:
	return Vector2(logical.x, logical.z)


func _owned_entity() -> Node:
	var entity_manager := get_tree().get_first_node_in_group("entity_manager")
	return entity_manager.get_owned_entity() if entity_manager else null
