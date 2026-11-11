class_name BestiaOrdersPanel
extends PanelContainer

## The orders of one owned bestia: what it does while nobody drives it. Beginners pick a preset or a job and a
## courage step; experts open Fine-tune for the numbers and the "when ... then ..." rules. Same model as the
## companion app. Built in code, like [BreedingFarmPanel].
##
## A bestia that follows a preset takes everything from it but the numbers it keeps as its own, so the pickers
## show the preset's choices greyed out until the player edits the preset itself, which changes it for every
## bestia that follows it.

signal title_changed(title: String)

const _LABEL_WIDTH := 90
## How long the delete button waits for its second click.
const _DELETE_CONFIRM_SECONDS := 3.0

var _player_bestia_id: int = 0
## What the server says is in force, to tell whether the draft changed. Null until it answered.
var _sent: BestiaAiConfigSMSG
var _presets: Array[Dictionary] = []
var _places: Array[Dictionary] = []

## The draft: a sheet of its own, or the followed preset's sheet with [member _own] on top.
var _sheet: OrderSheet
var _preset_id: int = 0
var _own: Dictionary = {}
## The draft is the followed preset's sheet, and giving it saves the preset.
var _editing_preset := false
## The next order book answers the save of the preset being edited.
var _saving_preset := false
## What to pick once the order book shows a preset or area just named: {"kind": PLACE_AREA or 0, "name": String}.
var _pick_when_named: Dictionary = {}

var _body: VBoxContainer
var _loading: Label
var _preset: OptionButton
var _preset_hint: Label
var _save_as: Button
var _edit_preset: Button
var _delete_preset: Button
var _preset_form: OrderNameForm
var _area_here: Button
var _area_form: OrderNameForm
var _job: OptionButton
var _job_hint: Label
var _fallback: OptionButton
var _fallback_row: Control
var _courage: HSlider
var _courage_name: Label
var _area: OptionButton
var _chest: OptionButton
var _fine_tune_toggle: Button
var _fine_tune: VBoxContainer
var _numbers: OrderNumbersEditor
var _rules: OrderRulesEditor
var _give: Button


func _ready() -> void:
	add_theme_stylebox_override("panel", UiStyle.flat(UiStyle.PANEL_BG, 0, 0))
	_build()

	ConnectionManager.bestia_orders_received.connect(_on_orders)
	ConnectionManager.order_book_received.connect(_on_order_book)


## Shows the orders of [param bestia], asking the server for them and for the presets and places to pick from.
func open_for(bestia: BestiaInfo) -> void:
	_player_bestia_id = bestia.PlayerBestiaId
	_sent = null
	_body.visible = false
	_loading.visible = true
	title_changed.emit(tr("ORDERS_TITLE") % OwnedSlots.name_of(bestia))
	_area_here.tooltip_text = tr("ORDERS_AREA_HERE") % OwnedSlots.name_of(bestia)

	if ConnectionManager.is_ready_to_send():
		ConnectionManager.get_bestia_orders(_player_bestia_id)
		ConnectionManager.get_order_book()


func _on_orders(msg: BestiaAiConfigSMSG) -> void:
	if msg.PlayerBestiaId != _player_bestia_id:
		return

	# Also the answer to our own change, and a change from the companion app while the window is open: what the
	# server holds wins over a draft.
	_sent = msg
	_editing_preset = false
	_saving_preset = false
	_sheet = msg.Orders.Copy()
	_preset_id = msg.PresetId
	_own = msg.OwnOverrides.duplicate() if msg.PresetId != 0 else msg.Orders.Overrides.duplicate()
	_loading.visible = false
	_body.visible = true
	_show_draft()


## Also the answer to every change of presets and places made here. A saved preset ends its editing.
func _on_order_book(msg: OrderBookSMSG) -> void:
	_presets.assign(msg.Presets)
	_places.assign(msg.Places)
	if _sent == null:
		return

	_read_widgets()
	if _saving_preset or not _has_preset(_preset_id):
		_saving_preset = false
		_leave_preset_editing()
	_pick_named()
	_show_draft()


func _pick_named() -> void:
	if _pick_when_named.is_empty():
		return
	var wanted := _pick_when_named
	_pick_when_named = {}

	if wanted["kind"] == OrderVocabulary.PLACE_AREA:
		for place in _places:
			if place["kind"] == OrderVocabulary.PLACE_AREA and place["name"] == wanted["name"]:
				_sheet.AreaPlaceId = place["id"]
		return
	for preset in _presets:
		if preset["name"] == wanted["name"]:
			_preset_id = preset["id"]
			_sheet = _preset_sheet().Copy()
			_own = {}


func _has_preset(preset_id: int) -> bool:
	return preset_id == 0 or _presets.any(func(preset: Dictionary) -> bool: return preset["id"] == preset_id)


## Back to the bestia's orders on the preset; one deleted meanwhile leaves it the orders that were shown.
func _leave_preset_editing() -> void:
	_editing_preset = false
	if not _has_preset(_preset_id):
		_preset_id = 0
		_own = _sheet.Overrides.duplicate()
		return
	_sheet = _preset_sheet().Copy()
	if _sent.PresetId == _preset_id:
		_own = _sent.OwnOverrides.duplicate()


func _show_draft() -> void:
	var following := _preset_id != 0
	var locked := following and not _editing_preset

	_fill_preset_picker()
	_preset.disabled = _editing_preset
	_preset_hint.text = tr("ORDERS_EDITING_HINT") if _editing_preset else tr("ORDERS_FOLLOWING_HINT")
	_preset_hint.visible = following
	_save_as.visible = not following
	_edit_preset.visible = following
	_edit_preset.set_pressed_no_signal(_editing_preset)
	_delete_preset.visible = following and not _editing_preset
	_select_id(_job, _sheet.Job)
	_job_hint.text = tr("ORDERS_JOB_HINT_%d" % _sheet.Job)
	_fill_fallback_picker()
	_courage.set_value_no_signal(_sheet.Courage)
	_courage_name.text = OrderVocabulary.courage_name(_sheet.Courage)
	_fill_place_picker(_area, OrderVocabulary.PLACE_AREA, tr("ORDERS_AROUND_HOME"), _sheet.AreaPlaceId)
	_fill_place_picker(_chest, OrderVocabulary.PLACE_CHEST, tr("ORDERS_NEAREST_CHEST"), _sheet.ChestPlaceId)
	if _editing_preset:
		_numbers.set_numbers(_sheet.Overrides, {})
	else:
		_numbers.set_numbers(_own, _preset_sheet().Overrides if following else {})
	_rules.set_rules(_sheet.Rules)

	for picker in [_job, _fallback, _area, _chest]:
		picker.disabled = locked
	_area_here.disabled = locked
	_courage.editable = not locked
	_rules.set_editable(not locked)
	_give.text = tr("ORDERS_SAVE_PRESET") if _editing_preset else tr("ORDERS_GIVE")
	_update_give()


func _fill_preset_picker() -> void:
	_preset.clear()
	_preset.add_item(tr("ORDERS_OWN"), 0)
	for preset in _presets:
		_preset.add_item(tr("ORDERS_PRESET_ITEM") % [preset["name"], preset["followers"]], preset["id"])
	_select_id(_preset, _preset_id)


func _fill_fallback_picker() -> void:
	_fallback.clear()
	var fallbacks := OrderVocabulary.fallbacks_for(_sheet.Job)
	_fallback_row.visible = not fallbacks.is_empty()
	_fallback.add_item(tr("ORDERS_NO_FALLBACK"), OrderVocabulary.Job.UNSET)
	for job in fallbacks:
		_fallback.add_item(OrderVocabulary.job_name(job), job)
	if _sheet.FallbackJob not in fallbacks:
		_sheet.FallbackJob = OrderVocabulary.Job.UNSET
	_select_id(_fallback, _sheet.FallbackJob)


## A place deleted in the companion app may still be named by orders the window holds; it shows as the default.
func _fill_place_picker(picker: OptionButton, kind: int, default_name: String, selected_id: int) -> void:
	picker.clear()
	picker.add_item(default_name, 0)
	for place in _places:
		if place["kind"] == kind:
			picker.add_item(place["name"], place["id"])
	_select_id(picker, selected_id)


func _preset_sheet() -> OrderSheet:
	for preset in _presets:
		if preset["id"] == _preset_id:
			return preset["orders"]
	return _sheet


func _select_id(picker: OptionButton, id: int) -> void:
	var index := picker.get_item_index(id)
	picker.select(index if index >= 0 else 0)


func _on_preset_selected(index: int) -> void:
	_read_widgets()
	var chosen := _preset.get_item_id(index)
	if chosen == 0:
		# From what was in force, so leaving a preset changes nothing until the player does.
		_sheet.Overrides = _sheet.Overrides.merged(_own, true)
		_own = _sheet.Overrides.duplicate()
	else:
		var keeps_own := _sent.PresetId == chosen
		_preset_id = chosen
		_sheet = _preset_sheet().Copy()
		_own = _sent.OwnOverrides.duplicate() if keeps_own else {}
	_preset_id = chosen
	_show_draft()


func _on_job_selected(index: int) -> void:
	_read_widgets()
	_sheet.Job = _job.get_item_id(index)
	_show_draft()


func _on_courage_changed(value: float) -> void:
	_courage_name.text = OrderVocabulary.courage_name(int(value))
	_update_give()


## Takes what the player changed into the draft. The pickers of a bestia on a preset show the preset's choices,
## so reading them back changes nothing.
func _read_widgets() -> void:
	_sheet.Courage = int(_courage.value)
	_sheet.FallbackJob = _fallback.get_selected_id() if _fallback_row.visible else OrderVocabulary.Job.UNSET
	_sheet.AreaPlaceId = _area.get_selected_id()
	_sheet.ChestPlaceId = _chest.get_selected_id()
	_sheet.Rules.assign(_rules.get_rules())
	if _editing_preset:
		_sheet.Overrides = _numbers.get_numbers()
		return
	_own = _numbers.get_numbers()
	if _preset_id == 0:
		_sheet.Overrides = _own


## Two drafts with the same key give the same orders. On a preset that is only which preset and the own numbers.
func _key_of(sheet: OrderSheet, preset_id: int, own: Dictionary) -> String:
	if preset_id != 0:
		return JSON.stringify([preset_id, _sorted(own)])
	return JSON.stringify([
		sheet.Job, sheet.Courage, sheet.FallbackJob, _sorted(sheet.Overrides), sheet.Rules, sheet.AreaPlaceId,
		sheet.ChestPlaceId,
	])


func _sorted(numbers: Dictionary) -> Array:
	var pairs := []
	for key in OrderVocabulary.NUMBERS:
		if numbers.has(key):
			pairs.append([key, numbers[key]])
	return pairs


func _update_give() -> void:
	if _sent == null:
		_give.disabled = true
		return
	_read_widgets()
	if _editing_preset:
		_give.disabled = _key_of(_sheet, 0, {}) == _key_of(_preset_sheet(), 0, {})
		return
	var sent_own: Dictionary = _sent.OwnOverrides if _sent.PresetId != 0 else _sent.Orders.Overrides
	_give.disabled = _key_of(_sheet, _preset_id, _own) == _key_of(_sent.Orders, _sent.PresetId, sent_own)


func _on_give_pressed() -> void:
	if _give.disabled or not ConnectionManager.is_ready_to_send():
		return
	_read_widgets()
	if _editing_preset:
		_saving_preset = true
		ConnectionManager.save_order_preset(_preset_id, _preset_name(), _sheet)
	else:
		ConnectionManager.set_bestia_orders(_player_bestia_id, _sheet, _preset_id, _own)


func _preset_name() -> String:
	for preset in _presets:
		if preset["id"] == _preset_id:
			return preset["name"]
	return ""


## The preset starts from what the bestia does now, numbers set by hand included.
func _on_preset_named(preset_name: String, _radius: int) -> void:
	if not ConnectionManager.is_ready_to_send():
		return
	_read_widgets()
	_pick_when_named = {"kind": 0, "name": preset_name}
	ConnectionManager.save_order_preset(0, preset_name, _sheet)


func _on_edit_preset_toggled(on: bool) -> void:
	_read_widgets()
	if on:
		_editing_preset = true
		_sheet = _preset_sheet().Copy()
	else:
		_leave_preset_editing()
	_show_draft()


func _on_delete_preset_pressed() -> void:
	if _delete_preset.text != tr("ORDERS_DELETE_PRESET_CONFIRM"):
		_delete_preset.text = tr("ORDERS_DELETE_PRESET_CONFIRM")
		get_tree().create_timer(_DELETE_CONFIRM_SECONDS).timeout.connect(
			func() -> void: _delete_preset.text = tr("ORDERS_DELETE_PRESET")
		)
		return
	_delete_preset.text = tr("ORDERS_DELETE_PRESET")
	if ConnectionManager.is_ready_to_send():
		ConnectionManager.delete_order_preset(_preset_id)


func _on_area_named(area_name: String, radius: int) -> void:
	if not ConnectionManager.is_ready_to_send():
		return
	_pick_when_named = {"kind": OrderVocabulary.PLACE_AREA, "name": area_name}
	ConnectionManager.save_work_area(_player_bestia_id, area_name, radius)


func _build() -> void:
	var outer := UiStyle.vbox(8)
	add_child(UiStyle.margin(outer, 12, 10, 12, 12))

	_loading = UiStyle.label(tr("ORDERS_LOADING"), 13, UiStyle.MUTED)
	outer.add_child(_loading)

	_body = UiStyle.vbox(6)
	outer.add_child(_body)

	_preset = _add_picker(_body, tr("ORDERS_PRESET"), _on_preset_selected)
	_preset_hint = UiStyle.label("", 12, UiStyle.MUTED)
	_preset_hint.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	_preset_hint.custom_minimum_size = Vector2(300, 0)
	_body.add_child(_preset_hint)

	var preset_tools := UiStyle.hbox(6)
	_body.add_child(preset_tools)
	preset_tools.add_child(UiStyle.spacer())
	_save_as = _add_tool(preset_tools, tr("ORDERS_SAVE_AS_PRESET"))
	_edit_preset = _add_tool(preset_tools, tr("ORDERS_EDIT_PRESET"))
	_edit_preset.toggle_mode = true
	_edit_preset.toggled.connect(_on_edit_preset_toggled)
	_delete_preset = _add_tool(preset_tools, tr("ORDERS_DELETE_PRESET"))
	_delete_preset.pressed.connect(_on_delete_preset_pressed)
	_preset_form = OrderNameForm.new()
	_preset_form.submitted.connect(_on_preset_named)
	_body.add_child(_preset_form)
	_save_as.pressed.connect(func() -> void: _preset_form.ask(tr("ORDERS_PRESET_NAME"), false))

	_job = _add_picker(_body, tr("ORDERS_JOB"), _on_job_selected)
	for job in OrderVocabulary.JOBS:
		_job.add_item(OrderVocabulary.job_name(job), job)
	_job_hint = UiStyle.label("", 12, UiStyle.MUTED)
	_job_hint.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	_job_hint.custom_minimum_size = Vector2(300, 0)
	_body.add_child(_job_hint)

	_fallback = _add_picker(_body, tr("ORDERS_FALLBACK"), func(_i: int) -> void: _update_give())
	_fallback_row = _fallback.get_parent()
	_fallback.tooltip_text = tr("ORDERS_FALLBACK_HINT")

	var courage_row := _add_row(_body, tr("ORDERS_COURAGE"))
	_courage = HSlider.new()
	_courage.min_value = OrderVocabulary.COURAGE_STEPS.front()
	_courage.max_value = OrderVocabulary.COURAGE_STEPS.back()
	_courage.tick_count = OrderVocabulary.COURAGE_STEPS.size()
	_courage.ticks_on_borders = true
	_courage.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_courage.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	_courage.value_changed.connect(_on_courage_changed)
	courage_row.add_child(_courage)
	_courage_name = UiStyle.label("", 13, UiStyle.ACCENT)
	_courage_name.custom_minimum_size = Vector2(64, 0)
	courage_row.add_child(_courage_name)

	_area = _add_picker(_body, tr("ORDERS_AREA"), func(_i: int) -> void: _update_give())
	_area_here = Button.new()
	_area_here.text = "+"
	_area_here.pressed.connect(func() -> void: _area_form.ask(tr("ORDERS_AREA_NAME"), true))
	_area.get_parent().add_child(_area_here)
	_area_form = OrderNameForm.new()
	_area_form.submitted.connect(_on_area_named)
	_body.add_child(_area_form)
	_chest = _add_picker(_body, tr("ORDERS_CHEST"), func(_i: int) -> void: _update_give())

	_fine_tune_toggle = Button.new()
	_fine_tune_toggle.flat = true
	_fine_tune_toggle.alignment = HORIZONTAL_ALIGNMENT_LEFT
	_fine_tune_toggle.toggle_mode = true
	_fine_tune_toggle.text = "▸ " + tr("ORDERS_FINE_TUNE")
	_fine_tune_toggle.toggled.connect(func(on: bool) -> void:
		_fine_tune.visible = on
		_fine_tune_toggle.text = ("▾ " if on else "▸ ") + tr("ORDERS_FINE_TUNE"))
	_body.add_child(_fine_tune_toggle)

	_fine_tune = UiStyle.vbox(6)
	_fine_tune.visible = false
	_body.add_child(_fine_tune)
	_numbers = OrderNumbersEditor.new()
	_numbers.changed.connect(_update_give)
	_fine_tune.add_child(_numbers)
	_fine_tune.add_child(UiStyle.label(tr("ORDERS_RULES"), 13, UiStyle.MUTED))
	_rules = OrderRulesEditor.new()
	_rules.changed.connect(_update_give)
	_fine_tune.add_child(_rules)

	var buttons := UiStyle.hbox(8)
	_body.add_child(buttons)
	buttons.add_child(UiStyle.spacer())
	_give = Button.new()
	_give.text = tr("ORDERS_GIVE")
	_give.pressed.connect(_on_give_pressed)
	buttons.add_child(_give)


func _add_tool(row: HBoxContainer, text: String) -> Button:
	var button := Button.new()
	button.text = text
	button.add_theme_font_size_override("font_size", 12)
	row.add_child(button)
	return button


func _add_row(body: VBoxContainer, title: String) -> HBoxContainer:
	var row := UiStyle.hbox(8)
	var label := UiStyle.label(title, 13, UiStyle.MUTED)
	label.custom_minimum_size = Vector2(_LABEL_WIDTH, 0)
	row.add_child(label)
	body.add_child(row)
	return row


func _add_picker(body: VBoxContainer, title: String, on_selected: Callable) -> OptionButton:
	var picker := OptionButton.new()
	picker.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	picker.item_selected.connect(on_selected)
	_add_row(body, title).add_child(picker)
	return picker
