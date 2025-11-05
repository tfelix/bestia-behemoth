extends Control
class_name Inventory

signal inventory_updated()

var InventoryItem = preload("res://Game/UI/Inventory/InventoryItem/InventoryItem.tscn")

## This is only for testing. In reality you will receive the items from the server.
@export var preset_items: Array[InventoryItemResource]
@export var selected_entity_id: int

@onready var _usable_grid: GridContainer = %UsableGrid

var _items: Dictionary[int, Array]

func _ready() -> void:
	# TODO this must be replaced when you can select entities to point to the
	# currently selected entity instead.
	ConnectionManager.connect("self_received", _on_self_received)
	ConnectionManager.connect("entity_received", _on_entity_received)
	
	# We usually overlook the first inventory update because we did not yet
	# fully load but the server already send it out. So we request it again to be
	# sure.
	if ConnectionManager.is_ready_to_send():
		ConnectionManager.get_inventory()

	_render_items()


func _on_self_received(msg: SelfSMSG) -> void:
	selected_entity_id = msg.MasterEntityId
	_render_items()


func _on_entity_received(msg: EntitySMSG) -> void:
	# Skip when it is not adressing our own entity.
	if msg is InventoryComponentSMSG:
		print("Inventory: Received update for entity %s" % [msg.EntityId])
		var selected_entity_items = _items.get_or_add(msg.EntityId, [])
		selected_entity_items.clear()
		var item_db = ItemDB.get_instance()
		for itemMsg in msg.Items:
			# Get the ItemResource from the database
			var item_resource = item_db.get_item(itemMsg.ItemId)
			if item_resource == null:
				printerr("Item with ID %s not found in ItemDB" % [itemMsg.ItemId])
				continue

			# Create an InventoryItemResource
			var inv_item = InventoryItemResource.new()
			inv_item.item = item_resource
			inv_item.amount = itemMsg.Amount
			inv_item.player_item_id = itemMsg.UniqueId
			selected_entity_items.append(inv_item)
		_render_items()
		inventory_updated.emit()


func _render_items() -> void:
	if !_items.has(selected_entity_id):
		printerr("No items for selected entity %s" % [selected_entity_id])
		return

	var selected_entity_items = _items[selected_entity_id]

	for item in selected_entity_items:
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
	if !_items.has(selected_entity_id):
		return 0
	
	var selected_entity_items = _items[selected_entity_id]

	for inv_item in selected_entity_items:
		if inv_item.item.item_id == item_id:
			return inv_item.amount
	return 0


func add_item(item_resource: ItemResource, amount: int) -> void:
	var selected_entity_items = _items.get_or_add(selected_entity_id, [])

	# Check if item already exists
	for inv_item in selected_entity_items:
		if inv_item.item.item_id == item_resource.item_id:
			inv_item.amount += amount
			inventory_updated.emit()
			return

	# Add new item
	var new_inv_item = InventoryItemResource.new()
	new_inv_item.item = item_resource
	new_inv_item.amount = amount
	selected_entity_items.append(new_inv_item)
	inventory_updated.emit()


func remove_item(item_id: int, amount: int) -> bool:
	var selected_entity_items = _items[selected_entity_id]

	if selected_entity_items == null:
		return false

	for inv_item in selected_entity_items:
		if inv_item.item.item_id == item_id:
			if inv_item.amount >= amount:
				inv_item.amount -= amount
				if inv_item.amount <= 0:
					selected_entity_items.erase(inv_item)
				inventory_updated.emit()
				return true
			return false
	return false
