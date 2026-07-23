extends PanelContainer
class_name Inventory

signal inventory_updated()

var InventoryItem = preload("res://Game/UI/Inventory/InventoryItem/InventoryItem.tscn")

## This is only for testing. In reality you will receive the items from the server.
@export var preset_items: Array[InventoryItemResource]
@export var selected_entity_id: int

## Assigned at runtime by Game/UI/ui.gd - both live inside WidgetWindows that only instantiate
## their content in _ready(), so neither can be reached through an editor-wired NodePath.
var equipment: Equipment = null
var equipment_window: WidgetWindow = null

@onready var _usable_grid: GridContainer = %UsableGrid
@onready var _equip_grid: GridContainer = %EquipGrid

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
				printerr("Inventory: Item with ID %s not found in ItemDB" % [itemMsg.ItemId])
				continue

			# Create an InventoryItemResource
			var inv_item = InventoryItemResource.new()
			inv_item.item = item_resource
			inv_item.amount = itemMsg.Amount
			inv_item.player_item_id = itemMsg.UniqueId
			inv_item.equipped = itemMsg.Equipped
			selected_entity_items.append(inv_item)
		_render_items()
		inventory_updated.emit()


## Re-renders from the last known server state. Also connected to Equipment.equipment_updated, since
## putting an item on takes it out of this list without the inventory itself changing.
func refresh() -> void:
	_render_items()


func _render_items() -> void:
	if !_items.has(selected_entity_id):
		# Items for this entity are probably not loaded yet.
		return

	for child in _usable_grid.get_children():
		child.queue_free()
	for child in _equip_grid.get_children():
		child.queue_free()

	var selected_entity_items = _items[selected_entity_id]

	for item in selected_entity_items:
		# A worn item is still held (the server keeps it in the same container, only flagged), but
		# showing it in both places would let the player drag the same physical item twice.
		if item.equipped:
			continue

		var inv_item = InventoryItem.instantiate()
		inv_item.amount = item.amount
		inv_item.item = item.item
		inv_item.unique_id = item.player_item_id
		inv_item.inventory = self
		match item.item.type:
			ItemResource.ItemType.USABLE:
				_usable_grid.add_child(inv_item)
			ItemResource.ItemType.EQUIP:
				_equip_grid.add_child(inv_item)
			ItemResource.ItemType.ETC:
				pass


## Double-clicking an equipment item while the equipment window is open equips it into the slot the
## item itself declares, instead of falling through to "use". Returns false when that didn't apply,
## so the caller can do the normal use-item thing.
func try_quick_equip(item: ItemResource, unique_id: int) -> bool:
	if equipment == null or equipment_window == null or not equipment_window.visible:
		return false
	if item.type != ItemResource.ItemType.EQUIP:
		return false

	return equipment.equip_to_own_slot(item.item_id, unique_id)


func is_initialized_for_current_entity() -> bool:
	return _items.has(selected_entity_id)


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
