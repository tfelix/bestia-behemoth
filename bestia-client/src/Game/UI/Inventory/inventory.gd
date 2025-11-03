extends Control
class_name Inventory

signal inventory_updated()

var InventoryItem = preload("res://Game/UI/Inventory/InventoryItem/InventoryItem.tscn")

@export var items: Array[InventoryItemResource]

@onready var _usable_grid: GridContainer = %UsableGrid

func _ready() -> void:
	if ConnectionManager.is_ready_to_send():
		ConnectionManager.get_inventory()
	# if bestia has changed, request again from server
	_render_items()


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


func get_item_count(item_id: int) -> int:
	for inv_item in items:
		if inv_item.item.item_id == item_id:
			return inv_item.amount
	return 0


func add_item(item_resource: ItemResource, amount: int) -> void:
	# Check if item already exists
	for inv_item in items:
		if inv_item.item.item_id == item_resource.item_id:
			inv_item.amount += amount
			inventory_updated.emit()
			return

	# Add new item
	var new_inv_item = InventoryItemResource.new()
	new_inv_item.item = item_resource
	new_inv_item.amount = amount
	items.append(new_inv_item)
	inventory_updated.emit()


func remove_item(item_id: int, amount: int) -> bool:
	for inv_item in items:
		if inv_item.item.item_id == item_id:
			if inv_item.amount >= amount:
				inv_item.amount -= amount
				if inv_item.amount <= 0:
					items.erase(inv_item)
				inventory_updated.emit()
				return true
			return false
	return false


func update_from_server(server_items: Array) -> void:
	items.clear()
	for server_item in server_items:
		items.append(server_item)
	_render_items()
	inventory_updated.emit()
