extends Node
class_name Interactable

## Opt-in marker: attach as a child of any clickable object (chest, door, ...)
## to make the default mouse state swap to an interact cursor on hover and
## call on_interact() instead of the object's normal click behavior.

@export var hover_cursor: Texture2D


func on_interact() -> void:
	pass
