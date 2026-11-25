extends ConfirmationDialog
class_name EssenceSocketDialog
## Which held or worn item an Essence Gem goes into. Items of the essence's group are listed; the ones it cannot go
## into are greyed with the reason. The server checks the same, see EssenceGemScript.

signal item_picked(target_player_item_id: int)

@onready var _essence: Label = %Essence
@onready var _fits: Label = %Fits
@onready var _rows: VBoxContainer = %Rows

var _picked_id := 0
var _choices := ButtonGroup.new()


func _ready() -> void:
	title = tr("ESSENCE_SOCKET_TITLE")
	ok_button_text = tr("ESSENCE_SOCKET_CONFIRM")
	%Warning.text = tr("ESSENCE_SOCKET_WARNING")
	confirmed.connect(_on_confirmed)
	canceled.connect(queue_free)


## [param gem] and [param copies] are [InventoryItemResource]s: the gem in hand, and every copy held or worn.
func open_for(gem: InventoryItemResource, copies: Array) -> void:
	var essence = gem.held_essence
	var tier := str(essence.Tier)
	var species := BestiaDB.get_instance().get_bestia(int(essence.BestiaId))
	var group := species.essence_group if species != null else ""
	var tool: String = EssenceText.TOOL_BY_EFFECT.get(species.essence_effect, "") if species != null else ""
	var min_item_level := EssenceText.min_item_level(species, tier)

	_essence.text = ItemDetail.essence_line(essence)
	var socket_name := tr("TOOL_KIND_" + tool) if not tool.is_empty() else tr("SOCKET_GROUP_" + group)
	_fits.text = tr("ESSENCE_SOCKET_FITS").format({"group": socket_name})
	if min_item_level > 0:
		_fits.text = tr("ESSENCE_SOCKET_FITS_LEVEL").format({"group": socket_name, "level": min_item_level})

	var fitting := copies.filter(func(copy) -> bool: return copy.item != null and EssenceText.group_of(copy.item) == group)
	for copy in fitting:
		_rows.add_child(_row(copy, _why_not(copy, tier, min_item_level, tool)))
	if fitting.is_empty():
		_rows.add_child(UiStyle.label(tr("ESSENCE_SOCKET_NONE"), 12, UiStyle.MUTED))

	get_ok_button().disabled = true
	popup_centered()


func _row(copy: InventoryItemResource, why_not: String) -> Button:
	var row := Button.new()
	row.toggle_mode = true
	row.button_group = _choices
	row.focus_mode = Control.FOCUS_NONE
	row.alignment = HORIZONTAL_ALIGNMENT_LEFT
	row.disabled = not why_not.is_empty()
	row.text = _describe(copy) + ("   · " + why_not if not why_not.is_empty() else "")
	row.pressed.connect(_on_row_pressed.bind(copy.player_item_id))
	return row


func _describe(copy: InventoryItemResource) -> String:
	var name := copy.title if not copy.title.is_empty() else tr(copy.item.name_key)
	if copy.upgrade_level > 0:
		name += " +%d" % copy.upgrade_level
	var text := "%s   Lv %d   %s" % [name, copy.item_level, ItemDetail.sockets_line(copy.slots, copy.essences.size())]
	if copy.equipped:
		text += "   · " + tr("ESSENCE_SOCKET_WORN")
	return text


## Empty when the essence may go into [param copy]. [param tool] is the one tool it fits, or empty for any.
func _why_not(copy: InventoryItemResource, tier: String, min_item_level: int, tool: String) -> String:
	if not tool.is_empty() and copy.item.tool_kind != tool:
		return tr("ESSENCE_SOCKET_WRONG_TOOL").format({"tool": tr("TOOL_KIND_" + tool)})
	if copy.essences.size() >= copy.slots:
		return tr("ESSENCE_SOCKET_FULL")
	if copy.item_level < min_item_level:
		return tr("ESSENCE_SOCKET_TOO_LOW").format({"gem": tr("GEM_TIER_" + tier)})
	return ""


func _on_row_pressed(player_item_id: int) -> void:
	_picked_id = player_item_id
	get_ok_button().disabled = false


func _on_confirmed() -> void:
	item_picked.emit(_picked_id)
	queue_free()
