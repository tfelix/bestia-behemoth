class_name Chat
extends Control

@onready var chat_input: LineEdit = %ChatInput
@onready var lines_container: VBoxContainer = %Lines
@onready var scroll_container: ScrollContainer = %Scroll
@onready var chat_mode_option: OptionButton = %ChatMode
@onready var user_whisper_input: LineEdit = %UserWhisper

@export_range(0, 150, 1) var max_chat_lines: int = 100
@export var max_chat_history: int = 10

var _history: Array[String] = []
var _history_index: int = -1

## Maps ChatMode OptionButton index to Bnet.Mode enum int values (Party=0, Guild=1, Public=3).
## Index 0=Public(/s), 1=Party(/p), 2=Guild(/g)
const BNET_MODE_MAP: Array[int] = [3, 0, 1]

## Refusals reported through [method error_line] here, by [code]OperationError.CodeName[/code].
##
## The chat window is the client's only system-message channel, and for a chat refusal it is also the right
## one: a player whose message went nowhere has to be told in the window they typed it in, since a toast
## somewhere else would leave them retyping. The equip and chart refusals are here for want of anywhere
## better - neither the equipment window nor the map shows text at all - and move the day either grows a
## status line of its own. A survey refused at cast start has nowhere else at all to be seen: it happens
## while the player is looking at the world, not at a window.
##
## Matching on the name rather than the ordinal keeps a new denial reason from being re-declared here as a bare
## number, which is the duplication [code]DialogArg.KindName[/code] was introduced to stop.
##
## A template may carry [code]%s[/code] placeholders, filled from [code]OperationError.Args[/code] in order.
## The server sends values and never a finished sentence, so the wording stays here and can be translated.
const _REFUSALS := {
	"basic_skill_chat_locked": "You cannot speak yet. Raise Basic Skill to Lv. 2 in your Skills window.",
	"basic_skill_party_locked": "Parties need Basic Skill Lv. 5.",
	"basic_skill_trade_locked": "Trading needs Basic Skill Lv. 1.",
	"equip_level_too_low": "You are not high enough level to wear that yet.",
	"chart_needs_blank": "You have no blank vellum to draw on.",
	"chart_not_found": "You are not carrying that chart.",
	"chart_merge_same": "A chart cannot be joined with itself.",
	"chart_stale_world": "That chart shows land that no longer exists.",
	"trade_target_unavailable": "They cannot trade right now.",
	"trade_out_of_range": "You are too far away to trade with them.",
	"trade_declined": "%s declined the trade.",
	"trade_cancelled": "%s cancelled the trade.",
	"trade_walked_away": "The trade with %s was cancelled - you moved too far apart.",
	"trade_failed": "The trade could not be completed. Nothing changed hands.",
}

## Red, for something that did not happen: a refusal, a failure, an action the server turned down.
const _ERROR_COLOR := Color(0.90, 0.35, 0.35)

## Yellow, for something the player should know but was not refused - a notice rather than a denial.
##
## Distinct from [constant _ERROR_COLOR] because the two read differently at a glance, which is the whole
## value of colouring them at all: red is "that did not happen", yellow is "here is something you need".
const _SYSTEM_COLOR := Color(0.95, 0.82, 0.35)


func _ready() -> void:
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
## Public because the refusals in [constant _REFUSALS] are not the only things with nowhere else to be said -
## the map has no text of its own either, and a map that has stopped working has to be able to say so.
##
## The colour is fixed rather than a parameter: which of the two a message is belongs to the message, and a
## caller free to pass any colour is a caller free to make a denial look like a notice.
func system_line(text: String) -> void:
	_add_chat_line(text, _SYSTEM_COLOR)


## Something the player asked for and did not get, in red.
func error_line(text: String) -> void:
	_add_chat_line(text, _ERROR_COLOR)


## Adds a new chat line in any colour, and makes sure not more than the allowed lines are added.
## If the chat was scrolled down it should scroll down too.
##
## Private: callers outside pick a *kind* of line - [method system_line] or [method error_line] - and the
## colour follows from that, so the palette stays in one place.
func _add_chat_line(text: String, color: Color = Color.WHITE) -> void:
	# Check if the scroll container is scrolled to the bottom
	var was_at_bottom = scroll_container.scroll_vertical >= scroll_container.get_v_scroll_bar().max_value - scroll_container.get_v_scroll_bar().page

	# Create new chat line label
	var new_line = Label.new()
	new_line.text = text
	new_line.layout_mode = 2
	new_line.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	if color != Color.WHITE:
		new_line.add_theme_color_override("font_color", color)

	# Add the new line to the container
	lines_container.add_child(new_line)

	# Remove old lines if we exceed max_chat_lines
	while lines_container.get_child_count() > max_chat_lines:
		var oldest_line = lines_container.get_child(0)
		lines_container.remove_child(oldest_line)
		oldest_line.queue_free()

	# Scroll to bottom if we were at bottom before adding the line
	if was_at_bottom:
		# Use call_deferred to ensure the scroll happens after the UI updates
		call_deferred("_scroll_to_bottom")


func _scroll_to_bottom() -> void:
	# Wait for the next frame to ensure layout is updated
	await get_tree().process_frame
	scroll_container.scroll_vertical = int(scroll_container.get_v_scroll_bar().max_value)


## Reports the refusals from [constant _REFUSALS] and ignores every other operation error, which belongs to
## whichever window raised it.
func _on_operation_error(message) -> void:
	var template: String = _REFUSALS.get(message.CodeName, "")
	if template.is_empty():
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
