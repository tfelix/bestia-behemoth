class_name OrderRulesEditor
extends VBoxContainer

## The "when ... then ..." lines of a bestia's orders, read from the top, the first that holds deciding.

signal changed()

var _rules: Array[Dictionary] = []
var _editable := true
var _lines: VBoxContainer
var _add: Button


func _init() -> void:
	add_theme_constant_override("separation", 4)
	_lines = UiStyle.vbox(4)
	add_child(_lines)

	_add = Button.new()
	_add.text = tr("ORDERS_ADD_RULE")
	_add.size_flags_horizontal = Control.SIZE_SHRINK_BEGIN
	_add.pressed.connect(_on_add_pressed)
	add_child(_add)


func set_rules(rules: Array[Dictionary]) -> void:
	_rules.clear()
	for rule in rules:
		_rules.append(rule.duplicate())
	_rebuild()


func get_rules() -> Array[Dictionary]:
	return _rules


## A bestia that follows a preset shows the preset's rules, but only the preset changes them.
func set_editable(editable: bool) -> void:
	_editable = editable
	_rebuild()


func _rebuild() -> void:
	for child in _lines.get_children():
		_lines.remove_child(child)
		child.queue_free()

	if _rules.is_empty():
		_lines.add_child(UiStyle.label(tr("ORDERS_NO_RULES"), 12, UiStyle.MUTED))
	for i in _rules.size():
		_lines.add_child(_line(i))

	_add.visible = _editable
	_add.disabled = _rules.size() >= OrderVocabulary.MAX_RULES


func _line(index: int) -> HBoxContainer:
	var rule: Dictionary = _rules[index]
	var row := UiStyle.hbox(4)

	var condition := OptionButton.new()
	for each in OrderVocabulary.conditions():
		condition.add_item(tr("ORDERS_CONDITION_%d" % each), each)
	condition.select(condition.get_item_index(rule["condition"]))
	condition.item_selected.connect(func(i: int) -> void:
		_rules[index]["condition"] = condition.get_item_id(i)
		_rules[index] = OrderVocabulary.fitted(_rules[index])
		_changed())
	row.add_child(condition)

	if OrderVocabulary.takes_threshold(rule["condition"]):
		var threshold := SpinBox.new()
		threshold.min_value = OrderVocabulary.THRESHOLD_MIN
		threshold.max_value = OrderVocabulary.THRESHOLD_MAX
		threshold.suffix = "%"
		threshold.value = rule["threshold"]
		threshold.value_changed.connect(func(value: float) -> void:
			_rules[index]["threshold"] = int(value)
			changed.emit())
		threshold.editable = _editable
		row.add_child(threshold)

	row.add_child(UiStyle.label(tr("ORDERS_RULE_THEN"), 12, UiStyle.MUTED))

	var action := OptionButton.new()
	for each in OrderVocabulary.actions_for(rule["condition"]):
		action.add_item(tr("ORDERS_ACTION_%d" % each), each)
	action.select(action.get_item_index(rule["action"]))
	action.item_selected.connect(func(i: int) -> void:
		_rules[index]["action"] = action.get_item_id(i)
		changed.emit())
	row.add_child(action)

	condition.disabled = not _editable
	action.disabled = not _editable
	if _editable:
		row.add_child(UiStyle.spacer())
		row.add_child(_glyph("↑", "ORDERS_RULE_UP", index > 0, func() -> void: _swap(index, index - 1)))
		row.add_child(_glyph("↓", "ORDERS_RULE_DOWN", index < _rules.size() - 1, func() -> void: _swap(index, index + 1)))
		row.add_child(_glyph("✕", "ORDERS_RULE_REMOVE", true, func() -> void:
			_rules.remove_at(index)
			_changed()))
	return row


func _glyph(glyph: String, tooltip_key: String, enabled: bool, on_pressed: Callable) -> Button:
	var button := UiStyle.glyph_button(glyph, tr(tooltip_key))
	button.disabled = not enabled
	button.pressed.connect(on_pressed)
	return button


func _swap(a: int, b: int) -> void:
	var held := _rules[a]
	_rules[a] = _rules[b]
	_rules[b] = held
	_changed()


func _on_add_pressed() -> void:
	_rules.append(OrderVocabulary.new_rule())
	_changed()


## Deferred: the line that asked for the change is among those freed.
func _changed() -> void:
	_rebuild.call_deferred()
	changed.emit()
