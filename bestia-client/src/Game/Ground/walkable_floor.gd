extends StaticBody3D

## Attach to any walkable floor collider. Puts it in the "floor" group (used
## by MouseManager's per-frame ground raycast for skill/item targeting
## indicators) and relays discrete clicks to the mouse state machine.


func _ready() -> void:
	add_to_group("floor")


func _on_input_event(_camera: Node, event: InputEvent, position: Vector3, _normal: Vector3, _shape_idx: int) -> void:
	MouseManager.on_ground_input_event(position, event)
