extends Panel

@export var item: ItemResource
@export var amount: int

## Id of the backing item instance, or 0 for a plain stackable pile. Needed to name *which*
## physical item to equip when several copies are held.
@export var unique_id: int = 0

## Assigned by Inventory when this node is instantiated - used for the double-click-to-equip
## shortcut, which has to know whether the equipment window is currently open.
## Untyped on purpose: statically typing this as Inventory forces inventory_item.gd to resolve
## the Inventory class while it's parsed, and Inventory in turn preloads InventoryItem.tscn -
## a load cycle that Godot rejects with a "Busy" parse error.
var inventory = null

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
	return {"type": "item", "id": item.item_id, "unique_id": unique_id, "source": "inventory_item"}


func _can_drop_data(_at_position: Vector2, _data: Variant) -> bool:
	return false


func _gui_input(event: InputEvent) -> void:
	if event is InputEventMouseButton:
		if event.button_index == MOUSE_BUTTON_LEFT and event.double_click:
			# With the equipment window open, double clicking a piece of gear puts it on; anything
			# else (or a closed window) falls through to the normal "use this item".
			if inventory != null and inventory.try_quick_equip(item, unique_id):
				return
			item.use_item()
		elif event.button_index == MOUSE_BUTTON_RIGHT and event.pressed:
			print("Panel was right-clicked!")
