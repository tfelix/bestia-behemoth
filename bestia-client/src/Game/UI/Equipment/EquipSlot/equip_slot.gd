extends Panel
class_name EquipSlot

## One equipment slot in the [Equipment] window.
##
## Accepts items dragged out of the inventory (the same `inventory_item` drag payload
## [GroundDropZone] consumes) and unequips on double click. Both are only *requests*: the server
## decides and answers with a fresh EquipmentComponentSMSG, so this node never mutates its own state
## optimistically - it only ever renders what [Equipment] tells it to.

signal equip_requested(item_id: int, unique_id: int, slot: int)
signal unequip_requested(slot: int)

## Which slot this is, as an [enum EquipmentSlot.Slot].
@export var slot: int = 0

@onready var _icon: TextureRect = %Icon
@onready var _label: Label = %SlotLabel

## False when the wearer's species simply has no such slot - the slot then renders dimmed and
## refuses every drop.
var _available: bool = true
var _item: ItemResource = null
var _unique_id: int = 0


func _ready() -> void:
	_refresh()


## Called by [Equipment] whenever the worn item (or the wearer) changes. Pass null to empty the slot.
func set_item(item: ItemResource, unique_id: int) -> void:
	_item = item
	_unique_id = unique_id
	if is_node_ready():
		_refresh()


func set_available(available: bool) -> void:
	_available = available
	if is_node_ready():
		_refresh()


func _refresh() -> void:
	_icon.texture = _item.icon if _item != null else null
	_icon.visible = _item != null
	_label.visible = _item == null
	_label.text = EquipmentSlot.display_name(slot)
	modulate = Color(1, 1, 1, 1) if _available else Color(1, 1, 1, 0.35)
	tooltip_text = tr(_item.name_key) if _item != null else EquipmentSlot.display_name(slot)


func _can_drop_data(_at_position: Vector2, data: Variant) -> bool:
	if not _available or typeof(data) != TYPE_DICTIONARY or data.get("source") != "inventory_item":
		return false

	var item_resource := ItemDB.get_instance().get_item(data.get("id", 0)) as ItemResource
	if item_resource == null:
		return false

	return EquipmentSlot.from_item_value(item_resource.equip_slot) == slot


func _drop_data(_at_position: Vector2, data: Variant) -> void:
	equip_requested.emit(data.get("id", 0), data.get("unique_id", 0), slot)


func _gui_input(event: InputEvent) -> void:
	if event is InputEventMouseButton \
			and event.button_index == MOUSE_BUTTON_LEFT \
			and event.double_click \
			and _item != null:
		unequip_requested.emit(slot)
