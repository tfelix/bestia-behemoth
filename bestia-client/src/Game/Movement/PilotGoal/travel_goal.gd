class_name TravelGoal
extends PilotGoal


## Walk to a point on the map and stop there.


## How near the destination counts as arrived, in tiles. A tile is a metre, and insisting on the
## exact one would leave the player shuffling on the spot when the server's ground correction
## disagrees by half of one.
const _ARRIVAL_TILES := 1.5

var _tile_xz: Vector2


func _init(tile_xz: Vector2) -> void:
	_tile_xz = tile_xz


func destination() -> Vector2:
	return _tile_xz


func arrival_tiles() -> float:
	return _ARRIVAL_TILES
