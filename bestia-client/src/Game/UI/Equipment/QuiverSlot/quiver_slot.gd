extends Panel
class_name QuiverSlot

## One entry of the quiver in the [Equipment] window: a pointer at ammo in the bag, not a copy of it.
##
## Ammo is dropped in from the inventory, dragged between entries to change the order it is shot in, and
## double clicked out. Every change is only a request: [Equipment] sends the whole list and renders what the
## server answers.

signal ammo_dropped(item_id: int, index: int)
signal moved(from_index: int, to_index: int)
signal clear_requested(index: int)

## Position in the quiver, 0 is shot first.
@export var index: int = 0

@onready var _icon: TextureRect = %Icon
@onready var _number: Label = %Number
@onready var _count_panel: PanelContainer = %CountPanel
@onready var _count: Label = %Count

var _item: ItemResource = null
var _held: int = 0


func _ready() -> void:
	_refresh()


## Pass null to empty the entry. [param held] is how much of it the bag holds.
func set_ammo(item: ItemResource, held: int) -> void:
	_item = item
	_held = held
	if is_node_ready():
		_refresh()


func _refresh() -> void:
	_icon.texture = _item.get_icon() if _item != null else null
	_icon.visible = _item != null
	_number.text = str(index + 1)
	_number.visible = _item == null
	_count_panel.visible = _item != null
	_count.text = str(_held)
	# Kept while empty, so buying more of it refills the quiver; dimmed so it reads as nothing to shoot.
	modulate = Color(1, 1, 1, 0.45) if _item != null and _held == 0 else Color(1, 1, 1, 1)
	tooltip_text = tr("QUIVER_SLOT_EMPTY") if _item == null else tr(_item.name_key)


func _can_drop_data(_at_position: Vector2, data: Variant) -> bool:
	if typeof(data) != TYPE_DICTIONARY:
		return false
	if data.get("source") == "quiver_slot":
		return data.get("index") != index

	var item := ItemDB.get_instance().get_item(data.get("id", 0)) as ItemResource
	return data.get("source") == "inventory_item" and item != null and item.type == ItemResource.ItemType.AMMO


func _drop_data(_at_position: Vector2, data: Variant) -> void:
	if data.get("source") == "quiver_slot":
		moved.emit(data.get("index"), index)
	else:
		ammo_dropped.emit(data.get("id"), index)


func _get_drag_data(_at_position: Vector2) -> Variant:
	if _item == null:
		return null

	var preview := TextureRect.new()
	preview.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	preview.size = Vector2(40, 40)
	preview.texture = _icon.texture
	set_drag_preview(preview)

	return {"source": "quiver_slot", "index": index, "id": _item.item_id}


func _gui_input(event: InputEvent) -> void:
	if event is InputEventMouseButton \
			and event.button_index == MOUSE_BUTTON_LEFT \
			and event.double_click \
			and _item != null:
		clear_requested.emit(index)
