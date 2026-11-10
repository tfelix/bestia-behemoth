class_name SurveyGamePanel
extends PanelContainer

## The needle game a Cartography cast opens at a theodolite: stop the swinging needle inside the green arc, once
## per round. The stops go to the server as times since each round began, so it judges them by the same swing.
##
## Paper and ink are already spent, so closing the window gives the game up.

signal survey_opened()
signal survey_closed()

var _structure_id: int = 0
var _swing_period_ms: int = 1
var _arc_centres: PackedInt32Array
var _round: int = 0
var _round_started_ms: int = 0
var _stops := PackedInt64Array()

var _dial: SurveyDial
var _round_label: Label
var _stop: Button


func _ready() -> void:
	add_theme_stylebox_override("panel", UiStyle.flat(UiStyle.PANEL_BG, 0, 0))
	custom_minimum_size = Vector2(320, 260)
	_build()

	ConnectionManager.survey_minigame_opened.connect(_on_opened)
	visibility_changed.connect(_on_visibility_changed)
	set_process(false)


func _build() -> void:
	var column := UiStyle.vbox(8)
	add_child(UiStyle.margin(column, 12, 12, 12, 12))

	_round_label = UiStyle.label("", 14, UiStyle.ACCENT)
	_round_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	column.add_child(_round_label)

	_dial = SurveyDial.new()
	_dial.custom_minimum_size = Vector2(280, 150)
	_dial.size_flags_vertical = Control.SIZE_EXPAND_FILL
	column.add_child(_dial)

	var hint := UiStyle.label(tr("SURVEY_HINT"), 12, UiStyle.MUTED)
	hint.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	hint.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	column.add_child(hint)

	_stop = Button.new()
	_stop.text = tr("SURVEY_STOP")
	_stop.pressed.connect(_on_stop)
	column.add_child(_stop)


func is_open() -> bool:
	return _structure_id != 0


func _on_opened(msg: SurveyMinigameSMSG) -> void:
	_structure_id = msg.StructureId
	_swing_period_ms = max(msg.SwingPeriodMs, 1)
	_arc_centres = PackedInt32Array(msg.ArcCentresDegrees)
	_dial.arc_width_degrees = msg.ArcWidthDegrees
	_stops = PackedInt64Array()
	_start_round(0)
	set_process(true)
	survey_opened.emit()
	_stop.grab_focus()


func _start_round(index: int) -> void:
	_round = index
	_round_started_ms = Time.get_ticks_msec()
	_dial.arc_centre_degrees = _arc_centres[index]
	_round_label.text = tr("SURVEY_ROUND") % [index + 1, _arc_centres.size()]


func _process(_delta: float) -> void:
	var elapsed := Time.get_ticks_msec() - _round_started_ms
	_dial.needle_degrees = 90.0 * sin(TAU * elapsed / _swing_period_ms)


func _unhandled_key_input(event: InputEvent) -> void:
	if is_open() and event.is_action_pressed("ui_accept"):
		_on_stop()
		get_viewport().set_input_as_handled()


func _on_stop() -> void:
	if not is_open():
		return

	_stops.append(Time.get_ticks_msec() - _round_started_ms)
	if _round + 1 < _arc_centres.size():
		_start_round(_round + 1)
		return

	if ConnectionManager.is_ready_to_send():
		ConnectionManager.send_survey_stops(_structure_id, _stops)
	_close()


func _close() -> void:
	_structure_id = 0
	set_process(false)
	survey_closed.emit()


func _on_visibility_changed() -> void:
	# Closing the window gives the game up: the server only ever hears the stops of a finished game.
	if not visible and is_open():
		_close()
