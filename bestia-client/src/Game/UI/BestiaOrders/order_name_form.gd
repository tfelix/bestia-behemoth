class_name OrderNameForm
extends HBoxContainer

## Asks in place for the name of a new preset or work area, and for an area its radius.

signal submitted(text: String, radius: int)

## Must match OrderPreset.NAME_LENGTH and OrderPlace.NAME_LENGTH on the server.
const MAX_NAME_LENGTH := 32
const DEFAULT_RADIUS := 16

var _name: LineEdit
var _radius: SpinBox
var _save: Button


func _init() -> void:
	add_theme_constant_override("separation", 6)
	visible = false

	_name = LineEdit.new()
	_name.max_length = MAX_NAME_LENGTH
	_name.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_name.text_changed.connect(func(text: String) -> void: _save.disabled = text.strip_edges().is_empty())
	_name.text_submitted.connect(func(_text: String) -> void: _submit())
	add_child(_name)

	var limits: Vector2i = OrderVocabulary.NUMBERS["area_radius"]
	_radius = SpinBox.new()
	_radius.min_value = limits.x
	_radius.max_value = limits.y
	_radius.suffix = tr("ORDERS_NUMBER_UNIT_AREA_RADIUS")
	_radius.tooltip_text = tr("ORDERS_NUMBER_AREA_RADIUS")
	add_child(_radius)

	_save = Button.new()
	_save.text = tr("ORDERS_SAVE")
	_save.pressed.connect(_submit)
	add_child(_save)

	var cancel := Button.new()
	cancel.text = tr("ORDERS_CANCEL")
	cancel.pressed.connect(hide)
	add_child(cancel)


func ask(placeholder: String, with_radius: bool) -> void:
	_name.text = ""
	_name.placeholder_text = placeholder
	_radius.visible = with_radius
	_radius.value = DEFAULT_RADIUS
	_save.disabled = true
	show()
	_name.grab_focus()


func _submit() -> void:
	if _save.disabled:
		return
	hide()
	submitted.emit(_name.text.strip_edges(), int(_radius.value))
