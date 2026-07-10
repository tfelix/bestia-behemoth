extends PopupMenu
class_name ContextMenu

## Minimal generic right-click menu. Structure/wiring only for now - real
## per-target actions (Attack, Trade, Inspect, ...) are a follow-up feature.


func _ready() -> void:
	add_item("(no actions yet)")
	set_item_disabled(0, true)


func open_at(screen_position: Vector2) -> void:
	position = Vector2i(screen_position)
	popup()
