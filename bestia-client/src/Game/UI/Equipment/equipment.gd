extends PanelContainer
class_name Equipment

## The equipment window: what the currently active entity wears, one [EquipSlot] per slot.
##
## Which slots exist at all is [b]static content[/b] and deliberately never sent by the server: the
## master has every slot, a bestia only those its species declares in the client's own [BestiaDB].
## The server enforces the identical mask independently, so a slot greyed out here is also a slot
## the server will refuse - this copy only exists to avoid pointless round trips.

signal equipment_updated()

## Gear the driven entity took off and put on since its last snapshot, as
## [code]{item: ItemResource, upgrade_level: int}[/code] per piece. Whoever moved it: the player, or a skill
## that strips it off them.
signal gear_changed(taken_off: Array[Dictionary], put_on: Array[Dictionary])

## The server's Equipment.QUIVER_SIZE.
const QUIVER_SIZE := 5

@onready var _slot_nodes: Array = _collect_slot_nodes()
@onready var _quiver_nodes: Array = find_children("*", "Panel", true, false).filter(func(node): return node is QuiverSlot)

## Where the quiver counts come from: the arrows stay in the bag. Set by the UI root.
var inventory: Inventory:
	set(value):
		inventory = value
		inventory.inventory_updated.connect(_render_quiver)

var _master_entity_id: int = 0
var _selected_entity_id: int = 0
## Bestia template id per entity, cached from VisualComponentSMSG so the slot mask survives the
## window being closed when the component arrives.
var _bestia_id_by_entity: Dictionary = {}

## Worn items of the currently shown entity, by EquipmentSlot ordinal. Built directly from the
## live EquipmentComponentSMSG payload (see _on_entity_received) rather than re-read from
## EntityManager's Entity cache on every render: EntityManager updates that cache from the very
## same signal, and since EntityManager node readies after this one (see Game.tscn - UI is an
## earlier sibling than EntityManager, and Godot fires signal listeners in connection order),
## reading through the cache on the live path meant this window always rendered one equip change
## behind. The cache is still used, but only to seed _worn on first show / entity switch, mirroring
## Skills._seed_skill_points_from_cache / BuffList._on_controlled_entity_changed.
var _worn: Dictionary = {}

## Ammo item ids of the shown entity, in the order they are shot. Seeded and kept like [member _worn].
var _quiver: Array[int] = []

## What each owned entity wore in its last snapshot, as worn entries by unique id - kept for every entity
## rather than just the shown one, so taking control of a bestia does not make its gear read as new.
var _last_worn: Dictionary[int, Dictionary] = {}


func _ready() -> void:
	ConnectionManager.connect("self_received", _on_self_received)
	ConnectionManager.connect("entity_received", _on_entity_received)

	var entity_manager := EntityManager.get_instance()
	if entity_manager != null:
		entity_manager.controlled_entity_changed.connect(_on_controlled_entity_changed)

	for slot_node in _slot_nodes:
		slot_node.equip_requested.connect(_on_equip_requested)
		slot_node.unequip_requested.connect(_on_unequip_requested)

	for quiver_node in _quiver_nodes:
		quiver_node.ammo_dropped.connect(_on_ammo_dropped)
		quiver_node.moved.connect(_on_quiver_moved)
		quiver_node.clear_requested.connect(_on_quiver_clear_requested)

	_render()


func _collect_slot_nodes() -> Array:
	var found: Array = []
	for node in find_children("*", "Panel", true, false):
		if node is EquipSlot:
			found.append(node)
	return found


func _on_self_received(msg: SelfSMSG) -> void:
	_master_entity_id = msg.MasterEntityId


## Seeds from whatever the Entity node already has cached (see entity.gd/entity_manager.gd) so
## this is correct immediately, without waiting for the next live EquipmentComponentSMSG -
## mirrors Skills._seed_skill_points_from_cache / BuffList._on_controlled_entity_changed.
func _on_controlled_entity_changed(_previous_id: int, entity_id: int) -> void:
	_selected_entity_id = entity_id
	_seed_worn_from_cache()
	_render()


func _on_entity_received(msg: EntitySMSG) -> void:
	if msg is EquipmentComponentSMSG:
		var worn := _to_worn_by_slot(msg.Items)
		if msg.EntityId == _selected_entity_id:
			_worn = worn
			_quiver.assign(Array(msg.QuiverItemIds))
			_render()
		_announce_gear_change(msg.EntityId, worn)
		equipment_updated.emit()
	elif msg is VisualComponentSMSG and msg.Kind == VisualKind.BESTIA:
		_bestia_id_by_entity[msg.EntityId] = int(msg.VisualId)
		if msg.EntityId == _selected_entity_id:
			_seed_worn_from_cache()
			_render()


## Seeds _worn from whatever EntityManager already has cached for the now-shown entity - used
## on entity selection change, when there is no live EquipmentComponentSMSG payload at hand.
func _seed_worn_from_cache() -> void:
	var entity_manager := EntityManager.get_instance()
	var entity: Entity = entity_manager.get_entity(_selected_entity_id) if entity_manager else null
	_worn = entity.get_equipment() if entity else {}
	_quiver = entity.get_quiver().duplicate() if entity else []


func _to_worn_by_slot(items: Array) -> Dictionary:
	var by_slot: Dictionary = {}
	for equipped in items:
		by_slot[int(equipped.Slot)] = {
			"item_id": int(equipped.ItemId),
			"unique_id": int(equipped.UniqueId),
			"upgrade_level": int(equipped.UpgradeLevel),
			"durability": int(equipped.Durability),
			"max_durability": int(equipped.MaxDurability),
			"slots": int(equipped.Slots),
			"item_level": int(equipped.ItemLevel),
			"rolled_stats": Array(equipped.RolledStats),
			"crafted_by": equipped.CraftedBy,
			"crafted_in": equipped.CraftedIn,
		}
	return by_slot


## Reports what [param entity_id] took off and put on between its last snapshot and [param worn].
##
## Compared by unique id rather than by slot - the server splits even stackable gear into its own instance to
## wear it, so every worn piece has one. That way a resend finds nothing to say: the server answers a refused
## equip with the unchanged set, and resyncs it whenever the entity is seen afresh. The first snapshot of an
## entity is what it already wore, so it only seeds, the way the inventory's first bag does.
func _announce_gear_change(entity_id: int, worn: Dictionary) -> void:
	var now: Dictionary = {}
	for equipped in worn.values():
		now[equipped["unique_id"]] = equipped

	var seeded := _last_worn.has(entity_id)
	var before: Dictionary = _last_worn.get(entity_id, {})
	_last_worn[entity_id] = now

	# Only the driven entity's: that is the one the equipment window works on, so its changes are the ones the
	# player just made.
	if not seeded or entity_id != _selected_entity_id:
		return

	var taken_off := _gear_missing_from(before, now)
	var put_on := _gear_missing_from(now, before)
	if not taken_off.is_empty() or not put_on.is_empty():
		gear_changed.emit(taken_off, put_on)


## The pieces in [param worn] that [param other] lacks, as [signal gear_changed] carries them.
func _gear_missing_from(worn: Dictionary, other: Dictionary) -> Array[Dictionary]:
	var item_db = ItemDB.get_instance()
	var missing: Array[Dictionary] = []
	for unique_id in worn:
		if other.has(unique_id):
			continue

		var item_resource = item_db.get_item(worn[unique_id]["item_id"])
		if item_resource != null:
			missing.append({"item": item_resource, "upgrade_level": worn[unique_id]["upgrade_level"]})

	return missing


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
	_render_quiver()

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

		var detail := ItemDetail.as_bbcode(
			item_resource,
			equipped.get("upgrade_level", 0),
			equipped.get("durability", 0),
			equipped.get("max_durability", 0),
			equipped.get("slots", 0),
			equipped.get("item_level", 0),
			equipped.get("rolled_stats", []),
			equipped.get("crafted_by", ""),
			equipped.get("crafted_in", "")
		)
		slot_node.set_item(
			item_resource,
			equipped["unique_id"],
			detail,
			equipped.get("durability", 0),
			equipped.get("max_durability", 0)
		)


func _render_quiver() -> void:
	var item_db = ItemDB.get_instance()
	for quiver_node in _quiver_nodes:
		var item_id: int = _quiver[quiver_node.index] if quiver_node.index < _quiver.size() else 0
		var held := inventory.get_item_count(item_id) if inventory != null and item_id != 0 else 0
		quiver_node.set_ammo(item_db.get_item(item_id) if item_id != 0 else null, held)


## Puts [param item_id] at [param index], moving it there if it is already in the quiver. A full quiver
## pushes its last entry out.
func _on_ammo_dropped(item_id: int, index: int) -> void:
	var changed := _quiver.duplicate()
	changed.erase(item_id)
	changed.insert(mini(index, changed.size()), item_id)
	_send_quiver(changed.slice(0, QUIVER_SIZE))


func _on_quiver_moved(from_index: int, to_index: int) -> void:
	if from_index >= _quiver.size():
		return

	var changed := _quiver.duplicate()
	var item_id: int = changed.pop_at(from_index)
	changed.insert(mini(to_index, changed.size()), item_id)
	_send_quiver(changed)


func _on_quiver_clear_requested(index: int) -> void:
	if index >= _quiver.size():
		return

	var changed := _quiver.duplicate()
	changed.remove_at(index)
	_send_quiver(changed)


## Puts [param item_id] in the first free quiver entry; the inventory's double click. False when the quiver is
## full or already holds it.
func add_to_quiver(item_id: int) -> bool:
	if _quiver.has(item_id) or _quiver.size() >= QUIVER_SIZE:
		return false

	var changed := _quiver.duplicate()
	changed.append(item_id)
	_send_quiver(changed)
	return true


func _send_quiver(item_ids: Array[int]) -> void:
	if ConnectionManager.is_ready_to_send():
		ConnectionManager.set_quiver(item_ids)


func _on_equip_requested(player_item_id: int, slot: int) -> void:
	if ConnectionManager.is_ready_to_send():
		ConnectionManager.equip_item(player_item_id, slot)


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
func equip_to_own_slot(item_id: int, player_item_id: int) -> bool:
	var item_resource = ItemDB.get_instance().get_item(item_id)
	if item_resource == null:
		return false

	var slot := EquipmentSlot.from_item_value(item_resource.equip_slot)
	if slot < 0 or not EquipmentSlot.has_slot(_available_mask(), slot):
		return false

	_on_equip_requested(player_item_id, slot)
	return true
