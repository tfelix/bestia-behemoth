extends HBoxContainer
class_name StepBar

## The row of steps above the master creation panel. It shows where the player is and lets them jump
## back to any step they already reached.

signal step_pressed(index: int)

const CHECK_ICON := preload("res://Theme/Icons/check.svg")
const CURRENT_COLOR := Color("#f1efe8")
const DONE_COLOR := Color("#5dcaa5")
const OPEN_COLOR := Color("#888780")

@export var titles: PackedStringArray = []

var _buttons: Array[Button] = []


func _ready() -> void:
	for i in titles.size():
		if i > 0:
			add_child(_make_connector())
		var button := _make_step_button(i)
		_buttons.append(button)
		add_child(button)


## [param reached] is the furthest step the player may open: every step before it is complete.
func show_progress(current: int, reached: int) -> void:
	for i in _buttons.size():
		var button := _buttons[i]
		var is_done := i < reached and i != current
		button.disabled = i > reached
		button.icon = CHECK_ICON if is_done else null
		button.text = titles[i] if is_done else "%s  %s" % [i + 1, titles[i]]

		if i == current:
			_paint(button, CURRENT_COLOR)
		elif is_done:
			_paint(button, DONE_COLOR)
		else:
			_paint(button, OPEN_COLOR)


func _make_step_button(index: int) -> Button:
	var button := Button.new()
	button.flat = true
	button.focus_mode = Control.FOCUS_NONE
	button.add_theme_constant_override("icon_max_width", 16)
	button.pressed.connect(func(): step_pressed.emit(index))
	return button


func _make_connector() -> ColorRect:
	var line := ColorRect.new()
	line.custom_minimum_size = Vector2(28, 1)
	line.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	line.color = OPEN_COLOR
	return line


func _paint(button: Button, color: Color) -> void:
	for state in ["font_color", "font_hover_color", "font_pressed_color", "font_focus_color", "font_disabled_color",
			"icon_normal_color", "icon_hover_color", "icon_pressed_color", "icon_focus_color", "icon_disabled_color"]:
		button.add_theme_color_override(state, color)
