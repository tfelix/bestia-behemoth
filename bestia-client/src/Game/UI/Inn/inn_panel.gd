class_name InnPanel
extends PanelContainer

## An inn's counter: make it the save point, and pay for every bestia out in the world to be tended.
##
## Opened by every InnSMSG: an innkeeper or a player-built inn sends one when clicked, and after each request.
## Built in code, like [BestiaStoragePanel].

signal inn_opened()

## The last offer the server sent; null until one arrives.
var _inn: InnSMSG

var _place: Label
var _save_point: Label
var _make_save_point: Button
var _rows: VBoxContainer
var _nothing_to_tend: Label
var _tend: Button


func _ready() -> void:
	add_theme_stylebox_override("panel", UiStyle.flat(UiStyle.PANEL_BG, 0, 0))
	_build()
	ConnectionManager.inn_received.connect(_on_inn)


func _on_inn(msg: InnSMSG) -> void:
	var was_shown := _inn != null and is_visible_in_tree()
	_inn = msg
	_render()

	if not was_shown:
		inn_opened.emit()


func _build() -> void:
	var root := UiStyle.vbox(8)
	add_child(UiStyle.margin(root, 10, 8, 10, 10))

	_place = UiStyle.label("", 16, UiStyle.ACCENT)
	root.add_child(_place)

	var save_row := UiStyle.hbox(8)
	_save_point = UiStyle.label("", 13, UiStyle.MUTED)
	_save_point.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_save_point.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	save_row.add_child(_save_point)
	_make_save_point = Button.new()
	_make_save_point.text = tr("INN_MAKE_SAVE_POINT")
	_make_save_point.pressed.connect(func() -> void: ConnectionManager.set_inn_save_point(_inn.CounterEntityId))
	save_row.add_child(_make_save_point)
	root.add_child(save_row)

	root.add_child(HSeparator.new())
	root.add_child(UiStyle.label(tr("INN_TEND_TITLE"), 14))

	var scroll := ScrollContainer.new()
	scroll.size_flags_vertical = Control.SIZE_EXPAND_FILL
	scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	_rows = UiStyle.vbox(4)
	_rows.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	scroll.add_child(_rows)
	root.add_child(scroll)
	_nothing_to_tend = UiStyle.label(tr("INN_NOTHING_TO_TEND"), 13, UiStyle.MUTED)
	root.add_child(_nothing_to_tend)

	var footer := UiStyle.hbox(8)
	footer.add_child(UiStyle.spacer())
	_tend = Button.new()
	_tend.pressed.connect(func() -> void: ConnectionManager.tend_bestias(_inn.CounterEntityId, _total_price()))
	footer.add_child(_tend)
	root.add_child(footer)


func _render() -> void:
	_place.text = tr("INN_TITLE") % _inn.PlaceName
	_save_point.text = tr("INN_SAVE_POINT_HERE") if _inn.SavePointHere else tr("INN_SAVE_POINT_ELSEWHERE")
	_make_save_point.visible = not _inn.SavePointHere

	for child in _rows.get_children():
		child.queue_free()
	for tending in _inn.Tendings:
		_rows.add_child(_row(tending))

	var has_work := not _inn.Tendings.is_empty()
	_nothing_to_tend.visible = not has_work
	_tend.visible = has_work
	_tend.text = tr("INN_TEND") % UiStyle.group_digits(_total_price())


func _row(tending: InnTending) -> Control:
	var row := UiStyle.hbox(8)
	var name := tending.Name if not tending.Name.is_empty() else BreedingText.species_name(tending.BestiaId)
	var bestia := UiStyle.label(name, 13)
	bestia.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	row.add_child(bestia)
	if tending.Fallen:
		row.add_child(UiStyle.label(tr("INN_FALLEN"), 13, UiStyle.DANGER))
	row.add_child(UiStyle.label(tr("INN_PRICE") % UiStyle.group_digits(tending.Price), 13, UiStyle.ACCENT))

	return row


func _total_price() -> int:
	var total := 0
	for tending in _inn.Tendings:
		total += tending.Price
	return total
