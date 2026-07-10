class_name PathCalculator

## Simple tile-by-tile path from start to target: steps one tile at a time,
## diagonally while both axes still differ and straight once one axis has
## caught up. Ignores entity/terrain collision - just enough to turn a single
## click into a full path instead of a single waypoint. A smarter, collision
## aware pathfinder can replace this later without touching call sites.

static func calculate_tile_path(start: Vector3, target: Vector3) -> Array[Vector3]:
	var path: Array[Vector3] = []

	var cur_x := int(round(start.x))
	var cur_z := int(round(start.z))
	var target_x := int(round(target.x))
	var target_z := int(round(target.z))

	var total_steps := maxi(absi(target_x - cur_x), absi(target_z - cur_z))
	if total_steps == 0:
		return path

	var start_y := start.y
	var target_y := target.y

	for step in range(1, total_steps + 1):
		if cur_x < target_x:
			cur_x += 1
		elif cur_x > target_x:
			cur_x -= 1
		if cur_z < target_z:
			cur_z += 1
		elif cur_z > target_z:
			cur_z -= 1
		var y := lerpf(start_y, target_y, float(step) / float(total_steps))
		path.append(Vector3(cur_x, y, cur_z))

	return path
