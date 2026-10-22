class_name VisualBounds

## The box around a visual's meshes, in its parent's space. Meshes only: the health bar and name tag are
## billboards that would stretch the frame.
static func of_meshes(visual: Node3D) -> AABB:
	var bounds := AABB()
	var first := true
	for mesh: MeshInstance3D in visual.find_children("*", "MeshInstance3D", true, false):
		var mesh_bounds: AABB = mesh.global_transform * mesh.get_aabb()
		bounds = mesh_bounds if first else bounds.merge(mesh_bounds)
		first = false
	return bounds
