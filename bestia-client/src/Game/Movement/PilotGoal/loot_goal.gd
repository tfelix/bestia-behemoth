class_name LootGoal
extends PilotGoal


## Walk up to an item on the ground and pick it up.
##
## The sibling of [AttackGoal], with the same reach for the same reason: the server's MAX_LOOT_RANGE is one
## tile under the same truncating distance, so 1.5 admits exactly the eight neighbours and the tile itself.
## Without the walk a click from further away was refused outright, and nothing on screen said why.
##
## Nothing is removed locally on arrival - the stack vanishes when the server says so, the way a collected prop
## does in CollectGoal.


const _REACH_TILES := 1.5

## The item we are walking to. Freed when the stack is picked up by someone else, rots away or its chunk
## unloads, which is the whole liveness check.
var _target: Node3D

var _entity_id: int

## Where it lies, cached: a stack on the ground does not move.
var _tile_xz: Vector2


func _init(target: Node3D, entity_id: int) -> void:
	_target = target
	_entity_id = entity_id

	# Drawn at the middle of its tile, so undoing that offset is what turns where it lies into the tile
	# coordinate the pilot steers in. See TileSpace.
	var at := target.global_position - TileSpace.CENTRE_OFFSET
	_tile_xz = Vector2(at.x, at.z)


func destination() -> Vector2:
	return _tile_xz


func arrival_tiles() -> float:
	return _REACH_TILES


func is_alive() -> bool:
	return is_instance_valid(_target)


func on_arrival() -> void:
	ConnectionManager.loot_item(_entity_id)
