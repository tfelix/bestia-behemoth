class_name InteractGoal
extends PilotGoal


## Walk to something and click on it in the server's sense: InteractEntityCMSG.
##
## The sibling of CollectGoal, and the same shape for the same reasons - nothing is applied locally, and the
## node's own validity is the liveness test. It differs in taking any node rather than a PropPicker, because
## the two things worth interacting with are drawn by different halves of the client: a finished station is a
## static prop with a picker, and a construction site is an ordinary entity with a StructureVisual.


## How close the player has to be, in tiles. Under the server's own reach, so the walk finishes inside the
## window rather than on its edge - see CollectGoal for why betting on the two agreeing to the metre is a bad
## bet on a slope.
const _REACH_TILES := 2.0

## The node we are walking to. Freed when its chunk unloads or its entity vanishes, which is what makes
## is_instance_valid the whole liveness check.
var _target: Node3D

var _entity_id: int

var _tile_xz: Vector2


func _init(target: Node3D, entity_id: int) -> void:
	_target = target
	_entity_id = entity_id

	# Drawn at the middle of its tile, so undoing that offset is what turns where it stands into the tile
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
	ConnectionManager.interact_entity(_entity_id)
