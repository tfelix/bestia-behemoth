class_name OrderItemsEditor
extends VBoxContainer

## A short list of items in a bestia's orders: each by name with a button to take it off, and a picker to add one
## of [member _choices].

signal changed()

var _items: Array[int] = []
var _choices: Array[ItemResource] = []
var _editable := true
var _chips: HFlowContainer
var _picker: OptionButton


func _init(title: String, choices: Array[ItemResource]) -> void:
	_choices = choices
	add_theme_constant_override("separation", 2)
	add_child(UiStyle.label(title, 12, UiStyle.MUTED))

	_chips = HFlowContainer.new()
	_chips.add_theme_constant_override("h_separation", 6)
	add_child(_chips)
	_picker = OptionButton.new()
	_picker.item_selected.connect(_on_picked)
	_chips.add_child(_picker)


func set_items(items: Array) -> void:
	_items.assign(items)
	_rebuild()


func get_items() -> Array[int]:
	return _items


## A bestia that follows a preset shows the preset's items, but only the preset changes them.
func set_editable(editable: bool) -> void:
	_editable = editable
	_rebuild()


func _rebuild() -> void:
	for child in _chips.get_children():
		if child != _picker:
			_chips.remove_child(child)
			child.queue_free()

	if _items.is_empty() and not _editable:
		_chips.add_child(UiStyle.label(tr("ORDERS_NO_ITEMS"), 12, UiStyle.MUTED))
	for i in _items.size():
		_chips.add_child(_chip(i))
	_chips.move_child(_picker, -1)
	_picker.visible = _editable and _items.size() < OrderVocabulary.MAX_ITEMS
	_fill_picker()


func _chip(index: int) -> HBoxContainer:
	var chip := UiStyle.hbox(2)
	chip.add_child(UiStyle.label(_name_of(_items[index]), 12))
	if _editable:
		var remove := UiStyle.glyph_button("✕", tr("ORDERS_ITEM_REMOVE"))
		remove.pressed.connect(func() -> void:
			_items.remove_at(index)
			_changed())
		chip.add_child(remove)
	return chip


## Its first entry only asks, so that picking any item is a change of selection.
func _fill_picker() -> void:
	_picker.clear()
	_picker.add_item(tr("ORDERS_ADD_ITEM"), 0)
	for item in _choices:
		if item.item_id not in _items:
			_picker.add_item(tr(item.name_key), item.item_id)
	_picker.select(0)


func _on_picked(index: int) -> void:
	var item_id := _picker.get_item_id(index)
	if item_id == 0:
		return
	_items.append(item_id)
	_changed()


## Deferred: the button that asked for the change is among those freed.
func _changed() -> void:
	_rebuild.call_deferred()
	changed.emit()


func _name_of(item_id: int) -> String:
	var item := ItemDB.get_instance().get_item(item_id)
	return tr(item.name_key) if item != null else "#%d" % item_id
