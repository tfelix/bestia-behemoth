class_name CollectGoal
extends PilotGoal


## Walk to a prop and collect it.
##
## Nothing is hidden locally on arrival - the prop disappears when the server says so, via
## StaticEntityRemovedSMSG. Optimistic removal would need an un-hide path for all three ways the
## server can refuse, and the client is genuinely able to be wrong about each of them.


## How close the player has to be for the collect to be worth sending, in tiles.
##
## Deliberately under the server's own MAX_COLLECT_RANGE of 3, so the walk finishes inside the
## window rather than on its edge - the server's Vec3L.distance is horizontal and truncating, and
## betting on the two agreeing to the metre would make arrival flaky on a slope.
const _REACH_TILES := 2.0

## The prop we are walking to. Its validity is the liveness test: the node is freed when the chunk
## unloads, when the manifest resets, and when the server says the prop is gone - so one check
## covers all three without any signal plumbing.
var _picker: PropPicker

## Where it stands, cached so the last leg can be aimed without touching a node that may be freed
## between one frame and the next.
var _tile_xz: Vector2


func _init(picker: PropPicker) -> void:
	_picker = picker

	# A prop is drawn at the middle of its tile, so undoing that offset is what turns where it stands
	# into the tile coordinate the pilot steers in. See TileSpace.
	var at := picker.global_position - TileSpace.CENTRE_OFFSET
	_tile_xz = Vector2(at.x, at.z)


func destination() -> Vector2:
	return _tile_xz


func arrival_tiles() -> float:
	return _REACH_TILES


func is_alive() -> bool:
	return is_instance_valid(_picker)


func on_arrival() -> void:
	ConnectionManager.collect_prop(_picker.entity_id)
