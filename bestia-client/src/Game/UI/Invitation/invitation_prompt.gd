class_name InvitationPrompt
extends PanelContainer

## One invitation waiting for a Yes or a No: who asks, what to, and a bar running down to when the server stops
## waiting. Answered once - a click, or letting it run out, which sends nothing because the server has already
## forgotten the invitation by then.

## [param accepted] is the answer; never emitted on expiry.
signal answered(accepted: bool)

var _countdown: ProgressBar
var _timer: Timer
var _answered := false


## [param text] is the question as the player reads it; [param lifetime_seconds] how long it stays.
func setup(text: String, lifetime_seconds: float) -> void:
	add_theme_stylebox_override("panel", UiStyle.flat(UiStyle.ROW_BG, 6, 10, UiStyle.ACCENT, 1))
	custom_minimum_size = Vector2(320, 0)

	var rows := UiStyle.vbox(6)
	add_child(rows)

	var question := UiStyle.label(text, 14)
	question.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	question.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	rows.add_child(question)

	_countdown = UiStyle.bar(UiStyle.ACCENT, 3)
	_countdown.max_value = lifetime_seconds
	_countdown.value = lifetime_seconds
	rows.add_child(_countdown)

	var buttons := UiStyle.hbox(8)
	buttons.alignment = BoxContainer.ALIGNMENT_CENTER
	rows.add_child(buttons)

	var yes := Button.new()
	yes.text = tr("INVITATION_ACCEPT")
	yes.theme_type_variation = &"PrimaryButton"
	yes.custom_minimum_size = Vector2(80, 0)
	yes.pressed.connect(_answer.bind(true))
	buttons.add_child(yes)

	var no := Button.new()
	no.text = tr("INVITATION_DECLINE")
	no.custom_minimum_size = Vector2(80, 0)
	no.pressed.connect(_answer.bind(false))
	buttons.add_child(no)

	# Started when the prompt enters the tree, which is after this: the tray sets a prompt up before adding it.
	_timer = Timer.new()
	_timer.one_shot = true
	_timer.wait_time = lifetime_seconds
	_timer.autostart = true
	_timer.timeout.connect(queue_free)
	add_child(_timer)


func _process(_delta: float) -> void:
	if _timer != null and not _timer.is_stopped():
		_countdown.value = _timer.time_left


func _answer(accepted: bool) -> void:
	if _answered:
		return

	_answered = true
	answered.emit(accepted)
	queue_free()
