extends ConfirmationDialog
class_name RepairKitDialog
## Which held or worn item a repair kit mends. Items of the kit's kind that wear are listed; undamaged ones are
## greyed with the reason. The server checks the same, see RepairKitScript.

signal item_picked(target_player_item_id: int)

## `weapon_repair_kit` in items.yml; every other kit mends armour.
const WEAPON_KIT_ITEM_ID := 641

@onready var _mends: Label = %Mends
@onready var _rows: VBoxContainer = %Rows

var _picked_id := 0
var _choices := ButtonGroup.new()


func _ready() -> void:
	title = tr("REPAIR_KIT_TITLE")
	ok_button_text = tr("REPAIR_KIT_CONFIRM")
	confirmed.connect(_on_confirmed)
	canceled.connect(queue_free)


## [param copies] are the [InventoryItemResource]s held or worn. [param worn_durability] maps a worn copy's unique id
## to its durability: the slot knows it, the bag only learns it once the copy is taken off.
func open_for(kit_item_id: int, copies: Array, worn_durability: Dictionary) -> void:
	var mends_weapons := kit_item_id == WEAPON_KIT_ITEM_ID
	_mends.text = tr("REPAIR_KIT_MENDS_WEAPON" if mends_weapons else "REPAIR_KIT_MENDS_ARMOR")

	var fitting := copies.filter(func(copy) -> bool: return copy.item != null and copy.max_durability > 0 \
		and (copy.item.weapon_type != "" if mends_weapons else copy.item.armor_type > 0))
	for copy in fitting:
		var durability: int = worn_durability.get(copy.unique_id, copy.durability) if copy.equipped else copy.durability
		_rows.add_child(_row(copy, durability))
	if fitting.is_empty():
		_rows.add_child(UiStyle.label(tr("REPAIR_KIT_NONE_WEAPON" if mends_weapons else "REPAIR_KIT_NONE_ARMOR"), 12, UiStyle.MUTED))

	get_ok_button().disabled = true
	popup_centered()


func _row(copy: InventoryItemResource, durability: int) -> Button:
	var undamaged := durability >= copy.max_durability
	var row := Button.new()
	row.toggle_mode = true
	row.button_group = _choices
	row.focus_mode = Control.FOCUS_NONE
	row.alignment = HORIZONTAL_ALIGNMENT_LEFT
	row.disabled = undamaged
	row.text = _describe(copy, durability) + ("   · " + tr("REPAIR_KIT_UNDAMAGED") if undamaged else "")
	row.pressed.connect(_on_row_pressed.bind(copy.player_item_id))
	return row


func _describe(copy: InventoryItemResource, durability: int) -> String:
	var name := copy.title if not copy.title.is_empty() else tr(copy.item.name_key)
	if copy.upgrade_level > 0:
		name += " +%d" % copy.upgrade_level
	var text := "%s   %s %d/%d" % [name, tr("ITEM_DETAIL_DURABILITY"), durability, copy.max_durability]
	if copy.equipped:
		text += "   · " + tr("REPAIR_KIT_WORN")
	return text


func _on_row_pressed(player_item_id: int) -> void:
	_picked_id = player_item_id
	get_ok_button().disabled = false


func _on_confirmed() -> void:
	item_picked.emit(_picked_id)
	queue_free()
