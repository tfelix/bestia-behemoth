extends Panel

@export var item: ItemResource
@export var amount: int

@onready var _count: Label = %Count
@onready var _icon: TextureRect = %Icon


func _ready() -> void:
	_count.text = str(amount)
	_icon.texture = item.icon


func _get_drag_data(_at_position: Vector2) -> Variant:
	var preview: TextureRect = TextureRect.new()
	preview.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	preview.size = Vector2(50, 50)
	preview.pivot_offset = preview.size / 2.0
	preview.rotation_degrees = 10
	set_drag_preview(preview)
	preview.texture = _icon.texture
	return {"type": "item", "id": item.item_id}


func _can_drop_data(_at_position: Vector2, _data: Variant) -> bool:
	return false


func _gui_input(event: InputEvent) -> void:
	if event is InputEventMouseButton:
		if event.button_index == MOUSE_BUTTON_LEFT and event.double_click:
			# use the item.
			print("Panel was double-clicked with left mouse button!")
		elif event.button_index == MOUSE_BUTTON_RIGHT and event.pressed:
			print("Panel was right-clicked!")
