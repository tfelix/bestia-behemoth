class_name OrderNumbersEditor
extends GridContainer

## The Fine-tune numbers. A number left unticked keeps what the courage step and the job choose, shown as "auto",
## or on a preset what the preset says, shown greyed out.

signal changed()

## key -> {"set": CheckBox, "value": SpinBox, "auto": Label}
var _rows: Dictionary = {}


func _init() -> void:
	columns = 3
	add_theme_constant_override("h_separation", 8)
	for key in OrderVocabulary.NUMBERS:
		var limits: Vector2i = OrderVocabulary.NUMBERS[key]
		var name_label := UiStyle.label(tr("ORDERS_NUMBER_%s" % key.to_upper()), 13, UiStyle.MUTED)
		name_label.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		add_child(name_label)

		var is_set := CheckBox.new()
		is_set.tooltip_text = tr("ORDERS_NUMBER_SET_HINT")
		add_child(is_set)

		var value := SpinBox.new()
		value.min_value = limits.x
		value.max_value = limits.y
		value.suffix = tr("ORDERS_NUMBER_UNIT_%s" % key.to_upper())
		value.custom_minimum_size = Vector2(130, 0)
		var auto := UiStyle.label(tr("ORDERS_NUMBER_AUTO"), 13, UiStyle.MUTED)
		auto.custom_minimum_size = Vector2(130, 0)
		var cell := UiStyle.hbox(0)
		cell.add_child(value)
		cell.add_child(auto)
		add_child(cell)

		var row := {"set": is_set, "value": value, "auto": auto}
		is_set.toggled.connect(func(on: bool) -> void:
			_show(row, on, false)
			changed.emit())
		value.value_changed.connect(func(_v: float) -> void: changed.emit())
		_rows[key] = row


## Shows [param own] as the numbers set by hand, and [param underneath] where none is set.
func set_numbers(own: Dictionary, underneath: Dictionary) -> void:
	for key in _rows:
		var row: Dictionary = _rows[key]
		var value: SpinBox = row["value"]
		(row["set"] as CheckBox).set_pressed_no_signal(own.has(key))
		value.set_value_no_signal(own.get(key, underneath.get(key, value.min_value)))
		_show(row, own.has(key), underneath.has(key))


## Ticking a number starts it from what was shown, so only an "auto" one jumps to its lowest value.
func _show(row: Dictionary, is_set: bool, has_underneath: bool) -> void:
	var value: SpinBox = row["value"]
	value.editable = is_set
	value.visible = is_set or has_underneath
	(row["auto"] as Label).visible = not value.visible


func get_numbers() -> Dictionary:
	var numbers := {}
	for key in _rows:
		if (_rows[key]["set"] as CheckBox).button_pressed:
			numbers[key] = int((_rows[key]["value"] as SpinBox).value)
	return numbers
