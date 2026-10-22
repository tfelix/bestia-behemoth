extends RefCounted
class_name ShortcutLayout

## What sits on the shortcut bar of one master: a few layers for the master and for each of its bestias, so every
## entity keeps its own attacks. Below them lies one shared layer, which shows wherever an entity's own slot is
## empty, so a potion or the like sits on the same key for all of them.

const LAYER_COUNT := 3
## The shared layer is shown and edited like one more layer after the entity's own ones.
const SHARED_LAYER := LAYER_COUNT
const MASTER_KEY := "master"
const FORMAT_VERSION := 2
## The bar had seven slots per row before layers. The seventh one moves to the second layer.
const _LEGACY_SLOT_NUMBER := 6

## Entity key -> { "active_layer": int, "layers": Array of { "row:number" -> ShortcutData } }.
var _entities: Dictionary = {}
var _shared: Dictionary = {}


static func entity_key_of(entity_id: int, master_entity_id: int) -> String:
	if entity_id == 0 or entity_id == master_entity_id:
		return MASTER_KEY
	return str(entity_id)


## What the slot does: its own content, or the shared slot below it when it is empty.
func resolve(entity_key: String, layer: int, row: int, number: int) -> ShortcutData:
	var own := get_slot(entity_key, layer, row, number)
	return own if not own.is_empty() else get_slot(entity_key, SHARED_LAYER, row, number)


func shows_shared(entity_key: String, layer: int, row: int, number: int) -> bool:
	return layer != SHARED_LAYER \
		and get_slot(entity_key, layer, row, number).is_empty() \
		and not get_slot(entity_key, SHARED_LAYER, row, number).is_empty()


func get_slot(entity_key: String, layer: int, row: int, number: int) -> ShortcutData:
	# A copy, because a ShortcutContainer edits the data it shows in place.
	var slot: ShortcutData = _layer(entity_key, layer).get(_slot_key(row, number))
	return slot.duplicate() if slot != null else ShortcutData.new()


func set_slot(entity_key: String, layer: int, row: int, number: int, data: ShortcutData) -> void:
	var slots := _layer(entity_key, layer)
	if data.is_empty():
		slots.erase(_slot_key(row, number))
	else:
		slots[_slot_key(row, number)] = data.duplicate()


func active_layer_of(entity_key: String) -> int:
	return _entity(entity_key)["active_layer"]


func set_active_layer(entity_key: String, layer: int) -> void:
	_entity(entity_key)["active_layer"] = layer


func to_dict() -> Dictionary:
	var entities := {}
	for key in _entities:
		var entity: Dictionary = _entities[key]
		var layers := []
		for slots in entity["layers"]:
			layers.append(_slots_to_list(slots))
		entities[key] = {"active_layer": entity["active_layer"], "layers": layers}
	return {"version": FORMAT_VERSION, "entities": entities, "shared": _slots_to_list(_shared)}


## Also reads the bar of one master as it was saved before layers: a flat list of slots.
static func from_dict(saved: Variant) -> ShortcutLayout:
	var layout := ShortcutLayout.new()
	if saved is Array:
		layout._migrate_single_bar(saved)
	elif saved is Dictionary:
		var entities: Dictionary = saved.get("entities", {})
		for key in entities:
			layout._read_entity(key, entities[key])
		layout._read_slots(MASTER_KEY, SHARED_LAYER, saved.get("shared", []))
	return layout


func _read_entity(key: String, saved: Dictionary) -> void:
	var saved_layers: Array = saved.get("layers", [])
	for layer in mini(saved_layers.size(), LAYER_COUNT):
		_read_slots(key, layer, saved_layers[layer])
	set_active_layer(key, clampi(int(saved.get("active_layer", 0)), 0, SHARED_LAYER))


func _migrate_single_bar(saved_slots: Array) -> void:
	for saved in saved_slots:
		var data := ShortcutData.from_dict(saved["data"])
		if int(saved["number"]) == _LEGACY_SLOT_NUMBER:
			set_slot(MASTER_KEY, 1, int(saved["row"]), 0, data)
		else:
			set_slot(MASTER_KEY, 0, int(saved["row"]), int(saved["number"]), data)


func _read_slots(key: String, layer: int, saved_slots: Array) -> void:
	for saved in saved_slots:
		set_slot(key, layer, int(saved["row"]), int(saved["number"]), ShortcutData.from_dict(saved["data"]))


static func _slots_to_list(slots: Dictionary) -> Array:
	var list := []
	for slot_key in slots:
		var position: PackedStringArray = slot_key.split(":")
		list.append({"row": int(position[0]), "number": int(position[1]), "data": slots[slot_key].to_dict()})
	return list


static func _slot_key(row: int, number: int) -> String:
	return "%d:%d" % [row, number]


func _entity(key: String) -> Dictionary:
	if not _entities.has(key):
		var layers := []
		for i in LAYER_COUNT:
			layers.append({})
		_entities[key] = {"active_layer": 0, "layers": layers}
	return _entities[key]


func _layer(entity_key: String, layer: int) -> Dictionary:
	if layer == SHARED_LAYER:
		return _shared
	return _entity(entity_key)["layers"][layer]
