extends Control

@onready var chat_input: LineEdit = %ChatInput
@onready var lines_container: VBoxContainer = %Lines
@onready var scroll_container: ScrollContainer = %Scroll
@onready var chat_mode_option: OptionButton = %ChatMode
@onready var user_whisper_input: LineEdit = %UserWhisper

@export_range(0, 150, 1) var max_chat_lines: int = 100
@export var max_chat_history: int = 10

var _history: Array[String] = []

## Maps ChatMode OptionButton index to Bnet.Mode enum int values (Party=0, Guild=1, Public=3).
## Index 0=Public(/s), 1=Party(/p), 2=Guild(/g)
const BNET_MODE_MAP: Array[int] = [3, 0, 1]


func _ready() -> void:
	ConnectionManager.connect("chat_received", _on_chat_received)


func _input(event):
	if event is InputEventKey and event.pressed:
		if event.keycode == KEY_ENTER:
			_handle_enter()
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


func _handle_clear_chat() -> void:
	for n in lines_container.get_children():
		n.queue_free()


func _switch_chat_mode(modeIdx: int) -> void:
	chat_mode_option.select(modeIdx)
	chat_mode_option.show()
	user_whisper_input.hide()


## Adds a new chat line and make sure not more than the allowed lines are added.
## If the chat was scrolled down it should scroll down too.
func _add_chat_line(text: String) -> void:
	# Check if the scroll container is scrolled to the bottom
	var was_at_bottom = scroll_container.scroll_vertical >= scroll_container.get_v_scroll_bar().max_value - scroll_container.get_v_scroll_bar().page

	# Create new chat line label
	var new_line = Label.new()
	new_line.text = text
	new_line.layout_mode = 2
	new_line.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART

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


func _on_chat_received(message: ChatSMSG) -> void:
	# TODO handle different colors for different chat modes and error code translations.
	if message.SenderName != "":
		_add_chat_line("%s: %s" % [message.SenderName, message.Text])
	else:
		_add_chat_line(message.Text)
