extends StructureKitUse
class_name BuildingPieceKitUse

## A kit for a building piece: its ghost snaps onto the ground and onto other pieces, on the server's 1 m grid,
## and shows red where the server would refuse it. See ChunkStreamManager.SnapStructure.


func snap_ghost(mgr: MouseManager, yaw: float) -> Variant:
	var ray = mgr.get_mouse_ray()
	if ray == null or ConnectionManager.chunk_stream == null:
		return null

	var spot: Vector4 = ConnectionManager.chunk_stream.SnapStructure(kind, ray[0], ray[1], yaw)
	if spot.w < 0.0:
		return null

	return {"tile": Vector3(spot.x, spot.y, spot.z), "valid": spot.w > 0.0}


func on_targeting_click(item: ItemResource, click_info: Dictionary) -> bool:
	# Waits for a spot that would be taken, rather than spending a click the server is sure to refuse.
	if not click_info.get("valid", false):
		return false

	return super.on_targeting_click(item, click_info)
