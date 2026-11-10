class_name LetterPanel
extends PanelContainer

## Letters by pigeon: the played master's mailbox on the left, the open letter or a new one on the right.
##
## Works anywhere: a pigeon finds the master, so no post office is needed. Built in code, like [CompendiumPanel].

signal notice(text: String)

## The player wants the Won & Bought window, which lists auction goods waiting at linked houses.
signal won_bought_requested()

## Must match Letter.MAX_SUBJECT_LENGTH and MAX_BODY_LENGTH on the server, which refuses anything longer.
const MAX_SUBJECT_LENGTH := 60
const MAX_BODY_LENGTH := 1000

## The postage, in gold. Only shown; the server charges it.
const POSTAGE := 1

const _LIST_WIDTH := 220

## The mailbox as last received, newest first.
var _letters: Array[LetterEntry] = []
var _open_letter_id: int = 0

var _list: VBoxContainer
var _empty: Label

var _reader: VBoxContainer
var _read_subject: Label
var _read_from: Label
var _read_kept: Label
var _read_body: Label

var _writer: VBoxContainer
var _to: LineEdit
var _subject: LineEdit
var _body: TextEdit
var _send: Button


func _ready() -> void:
	add_theme_stylebox_override("panel", UiStyle.flat(UiStyle.PANEL_BG, 0, 0))
	custom_minimum_size = Vector2(640, 420)
	_build()
	_show_reader(null)

	ConnectionManager.letters_received.connect(_on_letters)
	ConnectionManager.letter_arrived.connect(_on_arrived)
	ConnectionManager.letter_sent.connect(_on_sent)
	ConnectionManager.operation_error.connect(_on_refused)
	visibility_changed.connect(_on_visibility_changed)


## Opens the form with the recipient filled in, as Reply does.
func write_to(recipient: String, subject: String = "") -> void:
	_to.text = recipient
	_subject.text = subject
	_body.text = ""
	_show_writer()
	(_body if not recipient.is_empty() else _to).grab_focus()


func _on_visibility_changed() -> void:
	if is_visible_in_tree() and ConnectionManager.is_ready_to_send():
		ConnectionManager.request_letters()


func _on_letters(msg: LettersSMSG) -> void:
	_letters.assign(msg.Letters)
	_rebuild_list()

	var still_there := _letters.any(func(letter: LetterEntry) -> bool: return letter.LetterId == _open_letter_id)
	if not still_there and not _writer.visible:
		_show_reader(null)


func _on_arrived(msg: LetterArrivedSMSG) -> void:
	_letters.push_front(msg.Letter)
	_rebuild_list()


func _on_sent(msg: LetterSentSMSG) -> void:
	notice.emit(tr("LETTERS_SENT") % msg.RecipientName)
	_show_reader(_letter(_open_letter_id))


func _open(letter: LetterEntry) -> void:
	_show_reader(letter)

	if not letter.Read:
		letter.Read = true
		ConnectionManager.read_letter(letter.LetterId)
		_rebuild_list()


func _show_reader(letter: LetterEntry) -> void:
	_writer.visible = false
	_reader.visible = letter != null
	_open_letter_id = letter.LetterId if letter != null else 0
	if letter == null:
		return

	_read_subject.text = _subject_of(letter)
	_read_from.text = tr("LETTERS_FROM") % [letter.SenderName, _date(letter.SentAtEpochSeconds)]
	_read_kept.text = tr("LETTERS_KEPT") % AuctionFormat.time_left(letter.ExpiresAtEpochSeconds)
	_read_body.text = letter.Body


func _show_writer() -> void:
	_reader.visible = false
	_writer.visible = true
	_update_send()


## Disabled until the answer, so a double click pays twice for nothing. Chat reports a refusal.
func _on_send_pressed() -> void:
	_send.disabled = true
	ConnectionManager.send_letter(_to.text.strip_edges(), _subject.text.strip_edges(), _body.text.strip_edges())


func _on_refused(_msg: OperationError) -> void:
	if _writer.visible:
		_update_send()


func _on_reply_pressed() -> void:
	var letter := _letter(_open_letter_id)
	if letter != null:
		write_to(letter.SenderName, tr("LETTERS_RE") % _subject_of(letter))


func _on_delete_pressed() -> void:
	if _open_letter_id != 0:
		ConnectionManager.delete_letter(_open_letter_id)


## TextEdit has no length limit of its own.
func _on_body_changed() -> void:
	if _body.text.length() > MAX_BODY_LENGTH:
		_body.text = _body.text.left(MAX_BODY_LENGTH)
		_body.set_caret_column(_body.get_line(_body.get_line_count() - 1).length())
		_body.set_caret_line(_body.get_line_count() - 1)
	_update_send()


func _update_send() -> void:
	var has_text := not _subject.text.strip_edges().is_empty() or not _body.text.strip_edges().is_empty()
	_send.disabled = _to.text.strip_edges().is_empty() or not has_text


func _letter(letter_id: int) -> LetterEntry:
	for letter in _letters:
		if letter.LetterId == letter_id:
			return letter
	return null


func _subject_of(letter: LetterEntry) -> String:
	return letter.Subject if not letter.Subject.is_empty() else tr("LETTERS_NO_SUBJECT")


## Local time, day first: "05.11. 14:32".
func _date(epoch_seconds: int) -> String:
	var bias_seconds: int = Time.get_time_zone_from_system()["bias"] * 60
	var date := Time.get_datetime_dict_from_unix_time(epoch_seconds + bias_seconds)
	return "%02d.%02d. %02d:%02d" % [date["day"], date["month"], date["hour"], date["minute"]]


func _rebuild_list() -> void:
	for row in _list.get_children():
		row.queue_free()

	_empty.visible = _letters.is_empty()
	for letter in _letters:
		_list.add_child(_row(letter))


func _row(letter: LetterEntry) -> Button:
	var row := Button.new()
	row.custom_minimum_size = Vector2(0, 40)
	row.focus_mode = Control.FOCUS_NONE
	row.add_theme_stylebox_override("normal", UiStyle.flat(UiStyle.ROW_BG, 4, 6))
	row.add_theme_stylebox_override("hover", UiStyle.flat(UiStyle.ROW_HOVER_BG, 4, 6))
	row.add_theme_stylebox_override("pressed", UiStyle.flat(UiStyle.ROW_HOVER_BG, 4, 6))
	row.pressed.connect(_open.bind(letter))

	var lines := UiStyle.vbox(0)
	lines.mouse_filter = Control.MOUSE_FILTER_IGNORE
	var subject := UiStyle.label(_subject_of(letter), 13, UiStyle.ACCENT if not letter.Read else UiStyle.TEXT)
	subject.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS
	subject.mouse_filter = Control.MOUSE_FILTER_IGNORE
	lines.add_child(subject)
	var sender := UiStyle.label("%s · %s" % [letter.SenderName, _date(letter.SentAtEpochSeconds)], 11, UiStyle.MUTED)
	sender.mouse_filter = Control.MOUSE_FILTER_IGNORE
	lines.add_child(sender)

	var padded := UiStyle.margin(lines, 8, 2, 8, 2)
	padded.mouse_filter = Control.MOUSE_FILTER_IGNORE
	padded.set_anchors_preset(Control.PRESET_FULL_RECT)
	row.add_child(padded)
	return row


func _build() -> void:
	var columns := UiStyle.hbox(12)
	add_child(UiStyle.margin(columns, 12, 10, 12, 12))

	columns.add_child(_build_mailbox())
	var page := VBoxContainer.new()
	page.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	columns.add_child(page)
	page.add_child(_build_reader())
	page.add_child(_build_writer())


func _build_mailbox() -> Control:
	var column := UiStyle.vbox(8)
	column.custom_minimum_size = Vector2(_LIST_WIDTH, 0)

	var write := Button.new()
	write.text = tr("LETTERS_WRITE")
	write.pressed.connect(write_to.bind(""))
	column.add_child(write)

	var won_bought := Button.new()
	won_bought.text = tr("WON_BOUGHT_TITLE")
	won_bought.pressed.connect(func() -> void: won_bought_requested.emit())
	column.add_child(won_bought)

	var scroll := ScrollContainer.new()
	scroll.size_flags_vertical = Control.SIZE_EXPAND_FILL
	scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	column.add_child(scroll)

	var stack := UiStyle.vbox(4)
	stack.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	scroll.add_child(stack)

	_list = UiStyle.vbox(4)
	_list.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	stack.add_child(_list)

	_empty = UiStyle.label(tr("LETTERS_EMPTY"), 12, UiStyle.MUTED)
	_empty.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	stack.add_child(_empty)
	return column


func _build_reader() -> Control:
	_reader = UiStyle.vbox(6)
	_reader.size_flags_vertical = Control.SIZE_EXPAND_FILL

	_read_subject = UiStyle.label("", 16, UiStyle.ACCENT)
	_read_subject.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	_reader.add_child(_read_subject)
	_read_from = UiStyle.label("", 11, UiStyle.MUTED)
	_reader.add_child(_read_from)

	var scroll := ScrollContainer.new()
	scroll.size_flags_vertical = Control.SIZE_EXPAND_FILL
	scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	_reader.add_child(scroll)
	_read_body = UiStyle.label("", 13)
	_read_body.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	_read_body.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_read_body.vertical_alignment = VERTICAL_ALIGNMENT_TOP
	scroll.add_child(_read_body)

	var actions := UiStyle.hbox(8)
	_read_kept = UiStyle.label("", 11, UiStyle.MUTED)
	actions.add_child(_read_kept)
	actions.add_child(UiStyle.spacer())
	var delete := Button.new()
	delete.text = tr("LETTERS_DELETE")
	delete.pressed.connect(_on_delete_pressed)
	actions.add_child(delete)
	var reply := Button.new()
	reply.text = tr("LETTERS_REPLY")
	reply.pressed.connect(_on_reply_pressed)
	actions.add_child(reply)
	_reader.add_child(actions)
	return _reader


func _build_writer() -> Control:
	_writer = UiStyle.vbox(6)
	_writer.size_flags_vertical = Control.SIZE_EXPAND_FILL

	_to = LineEdit.new()
	_to.placeholder_text = tr("LETTERS_TO")
	_to.text_changed.connect(func(_text: String) -> void: _update_send())
	_writer.add_child(_to)

	_subject = LineEdit.new()
	_subject.placeholder_text = tr("LETTERS_SUBJECT")
	_subject.max_length = MAX_SUBJECT_LENGTH
	_subject.text_changed.connect(func(_text: String) -> void: _update_send())
	_writer.add_child(_subject)

	_body = TextEdit.new()
	_body.size_flags_vertical = Control.SIZE_EXPAND_FILL
	_body.wrap_mode = TextEdit.LINE_WRAPPING_BOUNDARY
	_body.text_changed.connect(_on_body_changed)
	_writer.add_child(_body)

	var actions := UiStyle.hbox(8)
	actions.add_child(UiStyle.spacer())
	var cancel := Button.new()
	cancel.text = tr("LETTERS_CANCEL")
	cancel.pressed.connect(func() -> void: _show_reader(_letter(_open_letter_id)))
	actions.add_child(cancel)
	_send = Button.new()
	_send.text = tr("LETTERS_SEND") % POSTAGE
	_send.pressed.connect(_on_send_pressed)
	actions.add_child(_send)
	_writer.add_child(actions)
	return _writer
