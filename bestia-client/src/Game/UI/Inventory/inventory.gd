extends Control

var InventoryItem = preload("res://Game/UI/Inventory/InventoryItem/InventoryItem.tscn")

@export var items: Array[InventoryItemResource]

@onready var _usable_grid: GridContainer = %UsableGrid

func _ready() -> void:
	# request inventory from server
	# if bestia has changed, request again from server
	_render_items()
	pass


func _render_items() -> void:
	for item in items:
		var inv_item = InventoryItem.instantiate()
		inv_item.amount = item.amount
		inv_item.item = item.item
		match item.item.type:
			ItemResource.ItemType.USABLE:
				_usable_grid.add_child(inv_item)
			ItemResource.ItemType.ETC:
				pass
			ItemResource.ItemType.EQUIP:
				pass


func _drop_data(_at_position: Vector2, data: Variant) -> void:
	# check if this is the button for the shortcut of this row.
	print("dropped on background", data)
	# if amount > 1 request amount from user
	# send 
	pass


func _can_drop_data(_at_position: Vector2, data: Variant) -> bool:
	return typeof(data) == TYPE_DICTIONARY and data["type"] == "item"
