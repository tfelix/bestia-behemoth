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


## How close the player has to be, in tiles.
##
## The server's basic attack reaches one tile, measured between tile coordinates with a truncating Euclidean
## distance - so every one of the eight neighbours counts as adjacent, and nothing further does. 1.5 is what
## admits exactly those: above [MovementPilot]'s own sqrt(2) floor, and below the 2.0 that would let the walk
## stop a tile short and swing at nothing.
const _REACH_TILES := 1.5

## What we are walking to. Freed when its entity vanishes or its chunk unloads, which is the whole liveness
## check - the same one CollectGoal and InteractGoal rely on.
var _target: Node3D

var _entity_id: int


func _init(target: Node3D, entity_id: int) -> void:
	_target = target
	_entity_id = entity_id


func destination() -> Vector2:
	# Drawn at the middle of its tile, so undoing that offset is what turns where it stands into the tile
	# coordinate the pilot steers in. See TileSpace.
	var at := _target.global_position - TileSpace.CENTRE_OFFSET

	return Vector2(at.x, at.z)


func arrival_tiles() -> float:
	return _REACH_TILES


func is_alive() -> bool:
	return is_instance_valid(_target)


func on_arrival() -> void:
	ConnectionManager.send_attack_entity(_entity_id)
