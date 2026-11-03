class_name AttackGoal
extends PilotGoal


## Walk into reach of something and swing at it.
##
## The sibling of [InteractGoal], with one difference that matters: creatures move, so the destination is read
## live rather than cached at construction. A prop cannot walk away between the click and the arrival; a boar
## can.
##
## One message is sent, on arrival. The standing order is the server's from then on - it keeps swinging until
## something ends it - so this goal does not follow a target that wanders off afterwards.


## The server measures reach between tile coordinates with a truncating Euclidean distance, so a reach of N
## tiles admits every tile closer than N + 1. Half a tile over N admits exactly those: for a swing that is 1.5,
## the eight neighbours and nothing further, and above [MovementPilot]'s own sqrt(2) floor.
const _TILE_SLACK := 0.5

## What we are walking to. Freed when its entity vanishes or its chunk unloads, which is the whole liveness
## check - the same one CollectGoal and InteractGoal rely on.
var _target: Node3D

var _entity_id: int

## How close the player has to be, in tiles: the reach of what the controlled entity holds.
var _reach_tiles: float


func _init(target: Node3D, entity_id: int, attack_range: int) -> void:
	_target = target
	_entity_id = entity_id
	_reach_tiles = maxi(attack_range, 1) + _TILE_SLACK


func destination() -> Vector2:
	# Drawn at the middle of its tile, so undoing that offset is what turns where it stands into the tile
	# coordinate the pilot steers in. See TileSpace.
	var at := _target.global_position - TileSpace.CENTRE_OFFSET

	return Vector2(at.x, at.z)


func arrival_tiles() -> float:
	return _reach_tiles


func is_alive() -> bool:
	return is_instance_valid(_target)


func on_arrival() -> void:
	ConnectionManager.send_attack_entity(_entity_id)
