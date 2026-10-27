extends VBoxContainer
class_name ExchangeColumn
## The other bag in the inventory window: one of the player's own entities standing next to the one they
## drive. Items dragged here from the inventory are handed over; items dragged out of here land in the
## inventory. Nothing moves locally - the server answers with both bags.

## Something dragged from the driven entity's bag was dropped on this column.
signal item_dropped(data: Dictionary)

## A row of this bag was shift-clicked or picked from its right-click menu. [param amount] 0 asks for one.
signal item_requested(row: Control, amount: int)

const _COLUMNS := 5

var InventoryItem = preload("res://Game/UI/Inventory/InventoryItem/InventoryItem.tscn")

var _title: Label
var _weight: Label
var _grid: GridContainer
var _flash: Tween


func _ready() -> void:
	add_theme_constant_override("separation", 6)
	custom_minimum_size = Vector2(_COLUMNS * 74, 0)

	_title = UiStyle.label("", 15, UiStyle.ACCENT)
	add_child(_title)

	_weight = UiStyle.label("", 12, UiStyle.MUTED)
	add_child(_weight)

	var scroll := ScrollContainer.new()
	scroll.size_flags_vertical = Control.SIZE_EXPAND_FILL
	scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	add_child(scroll)

	_grid = GridContainer.new()
	_grid.columns = _COLUMNS
	_grid.add_theme_constant_override("h_separation", 10)
	_grid.add_theme_constant_override("v_separation", 10)
	scroll.add_child(_grid)


## [param items] are the partner's [InventoryItemResource]s; worn ones stay out, as they do in the inventory.
func show_bag(title: String, items: Array, current_weight: int, max_weight: int) -> void:
	_title.text = title
	_weight.text = tr("EXCHANGE_WEIGHT") % [current_weight / 100.0, max_weight / 100.0] if max_weight > 0 else ""

	for child in _grid.get_children():
		child.queue_free()

	for item in items:
		if item.equipped:
			continue

		var row = InventoryItem.instantiate()
		row.amount = item.amount
		row.item = item.item
		row.unique_id = item.unique_id
		row.player_item_id = item.player_item_id
		row.crafted_by = item.crafted_by
		row.crafted_in = item.crafted_in
		row.durability = item.durability
		row.max_durability = item.max_durability
		row.slots = item.slots
		row.upgrade_level = item.upgrade_level
		row.item_level = item.item_level
		row.rolled_stats = item.rolled_stats
		row.drag_source = "exchange_partner"
		row.drop_target = self
		row.move_requested.connect(func(amount: int) -> void: item_requested.emit(row, amount))
		_grid.add_child(row)


## Shown when a drop is refused before it is sent, because the receiver could not carry it.
func flash_refusal() -> void:
	if _flash != null:
		_flash.kill()
	modulate = UiStyle.DANGER
	_flash = create_tween()
	_flash.tween_property(self, "modulate", Color.WHITE, 0.4)


func _can_drop_data(_at_position: Vector2, data: Variant) -> bool:
	return typeof(data) == TYPE_DICTIONARY and data.get("source") == "inventory_item"


func _drop_data(_at_position: Vector2, data: Variant) -> void:
	item_dropped.emit(data)
