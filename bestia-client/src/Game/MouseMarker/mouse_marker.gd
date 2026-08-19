extends Node3D

## Ground-tile cursor shown in MouseStateDefault ("click to move"). Tracks the
## floor raycast every frame (same source MouseStateSkillTargeting uses for
## the AOE cast indicator) and snaps to the center of the 1m tile under the
## mouse. Lives as a permanent node in Game.tscn instead of being instantiated
## per-state, since it should be visible for most of a play session rather
## than only for the lifetime of one cast.


func _process(_delta: float) -> void:
	if not (MouseManager.current_state is MouseStateDefault):
		visible = false
		return

	var hit = MouseManager.get_floor_hit_at_mouse()
	if hit == null:
		visible = false
		return

	visible = true
	# Snaps to the tile's min corner - MoverMarker's own local offset (0.5, y, 0.5) centers the mesh
	# within that tile visually, which is TileSpace.CENTRE_OFFSET expressed in the scene rather than
	# in code. The square therefore covers exactly the cell PathCalculator will walk to.
	var tile := TileSpace.world_to_tile(hit)
	global_position = Vector3(tile.x, hit.y, tile.z)
