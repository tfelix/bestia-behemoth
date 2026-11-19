class_name QuestDecideDialog
extends PanelContainer

## Asks the player to pick one choice at a decide step of a quest. A pick is final, so it gets a window of its
## own and is never one stray click in the log. "Not now" closes it; the log opens it again.

const _WIDTH := 400
const _EDGE := Color(0.26, 0.26, 0.31)
const _ARROW := "›"
const _ARROW_INSET := 14

var _quest_id: int = 0
var _quest_title: Label
var _question: Label
var _choices: VBoxContainer


func _ready() -> void:
	visible = false
	custom_minimum_size = Vector2(_WIDTH, 0)
	add_to_group("world_blocking_ui")
	add_theme_stylebox_override("panel", _frame())
	_build()
	minimum_size_changed.connect(_centre)


## Shows the decide [param objective] of [param quest]. The dialog is centred and stays until a pick or "Not now".
func open(quest, objective) -> void:
	_quest_id = quest.QuestId
	_quest_title.text = QuestStyle.title_of(quest).to_upper()
	_question.text = DialogText.resolve_ref(objective.Text)
	for child in _choices.get_children():
		child.queue_free()
	for choice in objective.Choices:
		_choices.add_child(_choice_button(choice))
	visible = true
	_centre()


## Closes the dialog when its quest no longer waits for a decision: it ended, was dropped, or was decided.
func close_unless_open(open_decide_quest_ids: Array) -> void:
	if visible and not open_decide_quest_ids.has(_quest_id):
		visible = false


func _unhandled_key_input(event: InputEvent) -> void:
	if visible and event.is_action_pressed("ui_cancel"):
		visible = false
		get_viewport().set_input_as_handled()


func _build() -> void:
	var rows := UiStyle.vbox(10)
	add_child(UiStyle.margin(rows, 20, 18, 20, 14))

	_quest_title = UiStyle.label("", 10, UiStyle.ACCENT)
	_quest_title.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	rows.add_child(_quest_title)

	_question = UiStyle.label("", 17)
	_question.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	_question.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	rows.add_child(_question)

	rows.add_child(HSeparator.new())

	_choices = UiStyle.vbox(8)
	rows.add_child(_choices)

	var foot := UiStyle.hbox(6)
	foot.add_child(UiStyle.label(tr("QUEST_DECIDE_FINAL"), 11, UiStyle.MUTED))
	foot.add_child(UiStyle.spacer())
	var later := Button.new()
	later.text = tr("QUEST_DECIDE_LATER")
	later.flat = true
	later.focus_mode = Control.FOCUS_NONE
	later.add_theme_font_size_override("font_size", 12)
	later.add_theme_color_override("font_color", UiStyle.MUTED)
	later.pressed.connect(hide)
	foot.add_child(later)
	rows.add_child(foot)


func _choice_button(choice) -> Button:
	var button := Button.new()
	button.text = DialogText.resolve_ref(choice.Text)
	button.alignment = HORIZONTAL_ALIGNMENT_LEFT
	button.focus_mode = Control.FOCUS_NONE
	button.add_theme_font_size_override("font_size", 14)
	button.add_theme_color_override("font_hover_color", UiStyle.ACCENT)
	button.add_theme_color_override("font_pressed_color", UiStyle.ACCENT)
	button.add_theme_stylebox_override("normal", _choice_box(UiStyle.ROW_BG, _EDGE))
	button.add_theme_stylebox_override("hover", _choice_box(UiStyle.ROW_HOVER_BG, UiStyle.ACCENT))
	button.add_theme_stylebox_override("pressed", _choice_box(UiStyle.ROW_HOVER_BG, UiStyle.ACCENT))
	button.add_child(_arrow_on(button))
	var choice_id: String = choice.Id
	button.pressed.connect(func() -> void:
		ConnectionManager.decide_quest(_quest_id, choice_id)
		visible = false
	)
	return button


## The mark at the right edge that says the row leads on. Lit with the row under the pointer.
func _arrow_on(button: Button) -> Label:
	var arrow := UiStyle.label(_ARROW, 16, UiStyle.MUTED)
	arrow.mouse_filter = Control.MOUSE_FILTER_IGNORE
	arrow.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
	arrow.set_anchors_and_offsets_preset(Control.PRESET_RIGHT_WIDE)
	arrow.offset_left = -_ARROW_INSET - 10
	arrow.offset_right = -_ARROW_INSET
	button.mouse_entered.connect(func() -> void: arrow.add_theme_color_override("font_color", UiStyle.ACCENT))
	button.mouse_exited.connect(func() -> void: arrow.add_theme_color_override("font_color", UiStyle.MUTED))
	return arrow


func _frame() -> StyleBoxFlat:
	var box := UiStyle.flat(UiStyle.PANEL_BG, 8, 0, UiStyle.ACCENT, 1)
	box.border_width_top = 3
	box.shadow_color = Color(0, 0, 0, 0.45)
	box.shadow_size = 12
	return box


func _choice_box(bg: Color, edge: Color) -> StyleBoxFlat:
	var box := UiStyle.flat(bg, 6, 0, edge, 1)
	box.content_margin_left = 14
	box.content_margin_right = 14
	box.content_margin_top = 11
	box.content_margin_bottom = 11
	return box


func _centre() -> void:
	size = get_combined_minimum_size()
	position = ((get_viewport_rect().size - size) / 2).round()
