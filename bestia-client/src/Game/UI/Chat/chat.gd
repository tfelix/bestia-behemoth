class_name Chat
extends Control

## The cursor came to rest on an item named in a line. Which window shows its details is [code]ui.gd[/code]'s
## to decide, the same way it owns the map popups.
signal item_hovered(item: ItemResource)
signal item_hover_ended()

@onready var chat_input: LineEdit = %ChatInput
@onready var lines_container: VBoxContainer = %Lines
@onready var scroll_container: ScrollContainer = %Scroll
@onready var chat_mode_option: OptionButton = %ChatMode
@onready var user_whisper_input: LineEdit = %UserWhisper

@export_range(0, 150, 1) var max_chat_lines: int = 100
@export var max_chat_history: int = 10

var _history: Array[String] = []
var _history_index: int = -1

## The line the cursor is currently over an item in. Kept because a line freed under the cursor never fires
## [signal RichTextLabel.meta_hover_ended], and two things free one: /clear and the [member max_chat_lines]
## trim - either of which would otherwise leave the details showing for the rest of the session.
var _hovered_line: RichTextLabel = null

## Maps ChatMode OptionButton index to Bnet.Mode enum int values (Party=0, Guild=1, Public=3).
## Index 0=Public(/s), 1=Party(/p), 2=Guild(/g)
const BNET_MODE_MAP: Array[int] = [3, 0, 1]

## Red, for something that did not happen: a refusal, a failure, an action the server turned down.
const _ERROR_COLOR := Color(0.90, 0.35, 0.35)

## Yellow, for something the player should know but was not refused - a notice rather than a denial.
##
## Distinct from [constant _ERROR_COLOR] because the two read differently at a glance, which is the whole
## value of colouring them at all: red is "that did not happen", yellow is "here is something you need".
const _SYSTEM_COLOR := Color(0.95, 0.82, 0.35)

## Blue, for something of the player's own that can be looked at more closely - the ink the map already draws
## their own marks in.
const _ITEM_COLOR := Color(0.62, 0.85, 1.0)


func _ready() -> void:
	add_to_group("world_blocking_ui")
	ConnectionManager.connect("chat_received", _on_chat_received)
	ConnectionManager.operation_error.connect(_on_operation_error)


func _input(event):
	if event is InputEventKey and event.pressed:
		if event.keycode == KEY_ENTER:
			_handle_enter()
			get_viewport().set_input_as_handled()
		elif chat_input.has_focus() and _history.size() > 0:
			if event.keycode == KEY_UP:
				_history_index = min(_history_index + 1, _history.size() - 1)
				chat_input.text = _history[_history_index]
				chat_input.caret_column = chat_input.text.length()
				get_viewport().set_input_as_handled()
			elif event.keycode == KEY_DOWN:
				_history_index -= 1
				if _history_index < 0:
					_history_index = -1
					chat_input.text = ""
				else:
					chat_input.text = _history[_history_index]
					chat_input.caret_column = chat_input.text.length()
				get_viewport().set_input_as_handled()


func _handle_enter() -> void:
	if chat_input.has_focus():
		if chat_input.text == "":
			chat_input.release_focus()
		else:
			_handle_chat_send()
	else:
		chat_input.call_deferred("grab_focus")

## Handles sending chat. Mode-switch prefixes (/s /p /g) update the UI and strip
## the prefix before sending. Plain /commands are passed as-is; ToEnvelope() in
## ChatCMSG.cs detects the leading slash and overrides the mode to Command(7).
func _handle_chat_send() -> void:
	var chat_text = chat_input.text
	_history.push_front(chat_text)
	if _history.size() > max_chat_history:
		_history.pop_back()

	# Detect internal mode switches.
	if chat_text.begins_with("/s "):
		_switch_chat_mode(0)
		chat_text = chat_text.substr(3)
	elif chat_text.begins_with("/p "):
		_switch_chat_mode(1)
		chat_text = chat_text.substr(3)
	elif chat_text.begins_with("/g "):
		_switch_chat_mode(2)
		chat_text = chat_text.substr(3)
	elif chat_text.begins_with("/w "):
		# FIXME whisper not yet implemented, we need to extract the username
		_clear_input()
		return
	
	# Special case handling for internal commands.
	if chat_text == "/clear":
		_handle_clear_chat()
	elif chat_text.begins_with("/"):
		# Mode 7 is the command type as this is a command for the server.
		ConnectionManager.send_chat(chat_input.text, 7)
	else:
		# Regular send to the server
		var bnet_mode = BNET_MODE_MAP[chat_mode_option.selected]
		ConnectionManager.send_chat(chat_text, bnet_mode)
	
	_clear_input()


func _clear_input() -> void:
	chat_input.text = ""
	chat_input.release_focus()
	_history_index = -1


func _handle_clear_chat() -> void:
	for n in lines_container.get_children():
		n.queue_free()


func _switch_chat_mode(modeIdx: int) -> void:
	chat_mode_option.select(modeIdx)
	chat_mode_option.show()
	user_whisper_input.hide()


## A neutral notice, in yellow.
##
## Public because the refusals handled in [method _on_operation_error] are not the only things with nowhere
## else to be said - the map has no text of its own either, and a map that has stopped working has to be able
## to say so.
##
## The colour is fixed rather than a parameter: which of the two a message is belongs to the message, and a
## caller free to pass any colour is a caller free to make a denial look like a notice.
func system_line(text: String) -> void:
	_add_chat_line(text, _SYSTEM_COLOR)


## Something the player asked for and did not get, in red.
func error_line(text: String) -> void:
	_add_chat_line(text, _ERROR_COLOR)


## The player came into possession of something. The item is named in its own colour and answers to a hover.
##
## Takes the item rather than a finished line for the reason the colour is not a parameter either: this is
## the only line rendered as markup, and a caller free to pass markup is free to pass anything.
func obtained_line(item: ItemResource, amount: int) -> void:
	# The count is inside the link as well as the name. At chat font size a bare item name is a small thing
	# to have to hit with the cursor.
	var named := tr("ITEM_OBTAINED_AMOUNT") % [amount, tr(item.name_key)]
	var link := "[url=%d][color=#%s]%s[/color][/url]" % [item.item_id, _ITEM_COLOR.to_html(false), named]

	var line := _add_rich_chat_line(tr("CHAT_ITEM_OBTAINED") % link)
	line.meta_hover_started.connect(_begin_item_hover.bind(line))
	line.meta_hover_ended.connect(func(_meta: Variant) -> void: _end_item_hover(line))
	line.tree_exiting.connect(func() -> void: _end_item_hover(line))


func _begin_item_hover(meta: Variant, line: RichTextLabel) -> void:
	# An item can leave the catalogue between a line being written and that line being hovered.
	var item := ItemDB.get_instance().get_item(int(meta))
	if item == null:
		return

	_hovered_line = line
	item_hovered.emit(item)


## Ends the hover only for the line that owns it, so a line trimmed off the top does not put out the details
## the player is reading about a different one.
func _end_item_hover(line: RichTextLabel) -> void:
	if _hovered_line != line:
		return

	_hovered_line = null
	item_hover_ended.emit()


## Adds a new chat line in any colour.
##
## Private: callers outside pick a *kind* of line - [method system_line] or [method error_line] - and the
## colour follows from that, so the palette stays in one place.
func _add_chat_line(text: String, color: Color = Color.WHITE) -> void:
	var new_line := Label.new()
	new_line.text = text
	new_line.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	if color != Color.WHITE:
		new_line.add_theme_color_override("font_color", color)

	_append_line(new_line)


## A line that carries markup, for the only kind of line this window writes itself.
##
## Deliberately not offered to callers outside, and deliberately not what [method _add_chat_line] became:
## every other line holds text a player or the server typed, and with BBCode enabled markup in that is markup
## they wrote - a [code][img][/code] in a public message would draw on everyone else's screen.
func _add_rich_chat_line(bbcode: String) -> RichTextLabel:
	var new_line := RichTextLabel.new()
	new_line.bbcode_enabled = true
	# Without both of these a RichTextLabel scrolls its own text inside a fixed height instead of growing to
	# fit the line, which in this container means a one-line-tall window.
	new_line.fit_content = true
	new_line.scroll_active = false
	new_line.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	# An underlined meta reads as a web link rather than as a thing in the world.
	new_line.meta_underlined = false
	new_line.text = bbcode

	_append_line(new_line)

	return new_line


## Hangs a line at the bottom, keeps the window inside [member max_chat_lines], and keeps a chat that was
## scrolled to the bottom at the bottom.
func _append_line(line: Control) -> void:
	var was_at_bottom = scroll_container.scroll_vertical >= scroll_container.get_v_scroll_bar().max_value - scroll_container.get_v_scroll_bar().page

	line.layout_mode = 2
	lines_container.add_child(line)

	while lines_container.get_child_count() > max_chat_lines:
		var oldest_line = lines_container.get_child(0)
		lines_container.remove_child(oldest_line)
		oldest_line.queue_free()

	if was_at_bottom:
		# Deferred so the scroll runs after the layout has taken the new line into account.
		call_deferred("_scroll_to_bottom")


func _scroll_to_bottom() -> void:
	# Wait for the next frame to ensure layout is updated
	await get_tree().process_frame
	scroll_container.scroll_vertical = int(scroll_container.get_v_scroll_bar().max_value)


## Reports an operation error as a chat refusal, and ignores every one this window has no wording for -
## which is every operation error except the chat, equip, chart and trade refusals: whichever window raised
## those handles them itself.
##
## The chat window is the client's only system-message channel, and for a chat refusal it is also the right
## one: a player whose message went nowhere has to be told in the window they typed it in, since a toast
## somewhere else would leave them retyping. The equip and chart refusals are here for want of anywhere
## better - neither the equipment window nor the map shows text at all - and move the day either grows a
## status line of its own.
##
## The translation key is built straight from [code]OperationError.CodeName[/code] - matching on the name
## rather than the ordinal is what keeps a new denial reason from being re-declared here as a bare number -
## so a new chat refusal needs only a [code]general.csv[/code] row, no entry in this file at all.
## [method Object.tr] echoes a missing key straight back, which is how a code with no row here - because it
## belongs to another window - is told apart from one this window actually owns.
##
## That silent return is load-bearing, and it cuts both ways: it is also what hid the whole table going dead
## when [code]CodeName[/code] lost its underscores, since a wrong key is indistinguishable from a key another
## window owns. Nothing at runtime can tell those apart, so the guard against it is [code]EnumNameTest[/code]
## in [code]tests/BestiaClient.Tests[/code], which checks every [code]ERROR_*[/code] row against the
## [code]OpError[/code] values at build time.
func _on_operation_error(message) -> void:
	var key := "ERROR_%s" % message.CodeName.to_upper()
	var template := tr(key)
	if template == key:
		return

	# A template with placeholders and no args would render "%s" at the player, so a mismatch falls back to
	# the raw template rather than a broken sentence.
	var text := template
	if message.Args.size() > 0:
		text = template % Array(message.Args)

	error_line(text)


func _on_chat_received(message: ChatSMSG) -> void:
	# TODO handle different colors for different chat modes and error code translations.
	if message.SenderName != "":
		_add_chat_line("%s: %s" % [message.SenderName, message.Text])
	else:
		_add_chat_line(message.Text)
