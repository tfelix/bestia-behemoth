class_name ReadableSheet
extends PanelContainer

## Shows what a thing in the world says when the master reads it: a grave stone, a signpost. A sheet of paper and
## not one more dark window, so it reads as something of the world. The chat keeps the line too.
##
## Only a text with a [code]_HEADER[/code] row beside its key gets the sheet. "You look at the well" stays a
## chat line.

const _WIDTH := 340
const _PAPER := Color(0.87, 0.81, 0.67)
const _PAPER_EDGE := Color(0.55, 0.46, 0.32)
const _INK := Color(0.22, 0.17, 0.12)
const _FAINT_INK := Color(0.45, 0.37, 0.27)
const _HEADER_SUFFIX := "_HEADER"

## How far down the screen the sheet's top edge sits: above the middle, where the master stands.
const _TOP_SHARE := 0.16

var _header: Label
var _body: Label
## What the sheet shows now, so a text that arrives late is not put under another thing's header.
var _shown: TextRef


func _ready() -> void:
	visible = false
	custom_minimum_size = Vector2(_WIDTH, 0)
	add_to_group("world_blocking_ui")
	add_theme_stylebox_override("panel", _sheet())
	_build()
	minimum_size_changed.connect(_place)
	ConnectionManager.readable_received.connect(_on_readable_received)


func _unhandled_key_input(event: InputEvent) -> void:
	if visible and event.is_action_pressed("ui_cancel"):
		visible = false
		get_viewport().set_input_as_handled()


func _on_readable_received(message: ReadableSMSG) -> void:
	var header_key: String = message.Text.Key + _HEADER_SUFFIX
	if tr(header_key) == header_key:
		return

	_header.text = tr(header_key).to_upper()
	_shown = message.Text
	TextSource.show_ref(message.Text, func(text: String):
		# A text written for this world may arrive after the master read something else.
		if _shown == message.Text:
			_body.text = text
	)
	visible = true
	_place()


func _build() -> void:
	var rows := UiStyle.vbox(10)
	add_child(UiStyle.margin(rows, 24, 20, 24, 14))

	_header = UiStyle.label("", 10, _FAINT_INK)
	_header.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	rows.add_child(_header)

	_body = UiStyle.label("", 14, _INK)
	_body.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	_body.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	_body.add_theme_constant_override("line_spacing", 5)
	rows.add_child(_body)

	var rule := HSeparator.new()
	var line := StyleBoxLine.new()
	line.color = _FAINT_INK
	rule.add_theme_stylebox_override("separator", line)
	rows.add_child(rule)

	var foot := UiStyle.hbox(6)
	foot.add_child(UiStyle.label(tr("READABLE_ALSO_IN_CHAT"), 10, _FAINT_INK))
	foot.add_child(UiStyle.spacer())
	var close := Button.new()
	close.text = tr("READABLE_CLOSE")
	close.flat = true
	close.focus_mode = Control.FOCUS_NONE
	close.add_theme_font_size_override("font_size", 12)
	close.add_theme_color_override("font_color", _INK)
	close.add_theme_color_override("font_hover_color", _FAINT_INK)
	close.pressed.connect(hide)
	foot.add_child(close)
	rows.add_child(foot)


func _sheet() -> StyleBoxFlat:
	var box := UiStyle.flat(_PAPER, 4, 0, _PAPER_EDGE, 1)
	box.shadow_color = Color(0, 0, 0, 0.45)
	box.shadow_size = 12
	return box


func _place() -> void:
	size = get_combined_minimum_size()
	var bounds := get_viewport_rect().size
	position = Vector2((bounds.x - size.x) / 2, bounds.y * _TOP_SHARE).round()
