extends Node3D

## Ground-tile cursor shown in MouseStateDefault ("click to move"). Tracks the
## floor raycast every frame (same source MouseStateSkillTargeting uses for
## the AOE cast indicator) and snaps to the center of the 1m tile under the
## mouse. Lives as a permanent node in Game.tscn instead of being instantiated
## per-state, since it should be visible for most of a play session rather
## than only for the lifetime of one cast.

const _TILE_SIZE: float = 1.0


func _process(_delta: float) -> void:
	if not (MouseManager.current_state is MouseStateDefault):
		visible = false
		return

	var hit = MouseManager.get_floor_hit_at_mouse()
	if hit == null:
		visible = false
		return

	visible = true
	# Snaps to the tile's min corner - MoverMarker's own local offset
	# (0.5, y, 0.5) centers the mesh within that tile visually.
	global_position = Vector3(
		floor(hit.x / _TILE_SIZE) * _TILE_SIZE,
		hit.y,
		floor(hit.z / _TILE_SIZE) * _TILE_SIZE
	)
