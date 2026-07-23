extends PanelContainer
class_name Equipment

## The equipment window: what the currently active entity wears, one [EquipSlot] per slot.
##
## Which slots exist at all is [b]static content[/b] and deliberately never sent by the server: the
## master has every slot, a bestia only those its species declares in the client's own [BestiaDB].
## The server enforces the identical mask independently, so a slot greyed out here is also a slot
## the server will refuse - this copy only exists to avoid pointless round trips.

signal equipment_updated()

@onready var _slot_nodes: Array = _collect_slot_nodes()

var _master_entity_id: int = 0
var _selected_entity_id: int = 0
## Bestia template id per entity, cached from BestiaVisualComponent so the slot mask survives the
## window being closed when the component arrives.
var _bestia_id_by_entity: Dictionary = {}

## Worn items of the currently shown entity, by EquipmentSlot ordinal. Built directly from the
## live EquipmentComponentSMSG payload (see _on_entity_received) rather than re-read from
## EntityManager's Entity cache on every render: EntityManager updates that cache from the very
## same signal, and since EntityManager node readies after this one (see Game.tscn - UI is an
## earlier sibling than EntityManager, and Godot fires signal listeners in connection order),
## reading through the cache on the live path meant this window always rendered one equip change
## behind. The cache is still used, but only to seed _worn on first show / entity switch, mirroring
## Skills._seed_skill_points_from_cache / BuffList._on_self_received.
var _worn: Dictionary = {}


func _ready() -> void:
	ConnectionManager.connect("self_received", _on_self_received)
	ConnectionManager.connect("entity_received", _on_entity_received)

	for slot_node in _slot_nodes:
		slot_node.equip_requested.connect(_on_equip_requested)
		slot_node.unequip_requested.connect(_on_unequip_requested)

	_render()


func _collect_slot_nodes() -> Array:
	var found: Array = []
	for node in find_children("*", "Panel", true, false):
		if node is EquipSlot:
			found.append(node)
	return found


## Seeds from whatever the Entity node already has cached (see entity.gd/entity_manager.gd) so
## this is correct immediately, without waiting for the next live EquipmentComponentSMSG -
## mirrors Skills._seed_skill_points_from_cache / BuffList._on_self_received.
func _on_self_received(msg: SelfSMSG) -> void:
	_master_entity_id = msg.MasterEntityId
	# TODO Follow the actually selected entity once entity selection exists client side; until then
	#   the master is always the acting entity, matching what the server resolves.
	_selected_entity_id = msg.MasterEntityId
	_seed_worn_from_cache()
	_render()


func _on_entity_received(msg: EntitySMSG) -> void:
	if msg is EquipmentComponentSMSG:
		if msg.EntityId == _selected_entity_id:
			_worn = _to_worn_by_slot(msg.Items)
			_render()
		equipment_updated.emit()
	elif msg is BestiaVisualComponent:
		_bestia_id_by_entity[msg.EntityId] = int(msg.BestiaId)
		if msg.EntityId == _selected_entity_id:
			_seed_worn_from_cache()
			_render()


## Seeds _worn from whatever EntityManager already has cached for the now-shown entity - used
## on entity selection change, when there is no live EquipmentComponentSMSG payload at hand.
func _seed_worn_from_cache() -> void:
	var entity_manager := EntityManager.get_instance()
	var entity: Entity = entity_manager.get_entity(_selected_entity_id) if entity_manager else null
	_worn = entity.get_equipment() if entity else {}


func _to_worn_by_slot(items: Array) -> Dictionary:
	var by_slot: Dictionary = {}
	for equipped in items:
		by_slot[int(equipped.Slot)] = {
			"item_id": int(equipped.ItemId),
			"unique_id": int(equipped.UniqueId),
		}
	return by_slot


## The slot mask of the currently shown entity. A master physically has every slot (whether it may
## actually wear a given item is a server-side rule); a bestia only what its species declares.
func _available_mask() -> int:
	if _selected_entity_id == _master_entity_id:
		return EquipmentSlot.ALL_MASK

	var bestia_id: int = _bestia_id_by_entity.get(_selected_entity_id, 0)
	if bestia_id == 0:
		return 0

	return BestiaDB.get_instance().get_equip_slots(bestia_id)


## Worn items of the currently shown entity, by EquipmentSlot ordinal.
func _worn_by_slot() -> Dictionary:
	return _worn


func _render() -> void:
	var mask := _available_mask()
	var by_slot: Dictionary = _worn_by_slot()
	var item_db = ItemDB.get_instance()

	for slot_node in _slot_nodes:
		slot_node.set_available(EquipmentSlot.has_slot(mask, slot_node.slot))

		var equipped = by_slot.get(slot_node.slot)
		if equipped == null:
			slot_node.set_item(null, 0)
			continue

		var item_resource = item_db.get_item(equipped["item_id"])
		if item_resource == null:
			printerr("Equipment: Item with ID %s not found in ItemDB" % [equipped["item_id"]])
			slot_node.set_item(null, 0)
			continue

		slot_node.set_item(item_resource, equipped["unique_id"])


func _on_equip_requested(item_id: int, unique_id: int, slot: int) -> void:
	if ConnectionManager.is_ready_to_send():
		ConnectionManager.equip_item(item_id, unique_id, slot)


func _on_unequip_requested(slot: int) -> void:
	if ConnectionManager.is_ready_to_send():
		ConnectionManager.unequip_item(slot)


## True if [param unique_id] is currently worn by the shown entity - used by [Inventory] so a worn
## item is not also listed as a loose inventory stack.
func is_worn(unique_id: int) -> bool:
	if unique_id == 0:
		return false
	for equipped in _worn_by_slot().values():
		if equipped["unique_id"] == unique_id:
			return true
	return false


## Equips [param item_id] into whichever slot the item itself declares. Used by the inventory's
## double-click-to-equip shortcut. Returns false when the item is not equipment at all.
func equip_to_own_slot(item_id: int, unique_id: int) -> bool:
	var item_resource = ItemDB.get_instance().get_item(item_id)
	if item_resource == null:
		return false

	var slot := EquipmentSlot.from_item_value(item_resource.equip_slot)
	if slot < 0 or not EquipmentSlot.has_slot(_available_mask(), slot):
		return false

	_on_equip_requested(item_id, unique_id, slot)
	return true
