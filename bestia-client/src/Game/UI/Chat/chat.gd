extends Control

@onready var chat_input: LineEdit = %ChatInput
@onready var lines_container: VBoxContainer = %Lines
@onready var scroll_container: ScrollContainer = %Scroll

@export_range(0, 150, 1) var max_chat_lines: int = 100


func _ready() -> void:
	ConnectionManager.connect("chat_received", _on_chat_received)


func _input(event):
	if event is InputEventKey and event.pressed:
		if event.keycode == KEY_ENTER:
			_handle_enter()


func _handle_enter() -> void:
	if chat_input.has_focus():
		if chat_input.text == "":
			chat_input.release_focus()
		else:
			# Chat has text. Send it.
			ConnectionManager.send_chat(chat_input.text)
			chat_input.text = ""
			chat_input.release_focus()
	else:
		chat_input.call_deferred("grab_focus")


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
	# TODO handle different colors for different chats
	if message.SenderName != "":
		_add_chat_line("%s: %s" % [message.SenderName, message.Text])
	else:
		_add_chat_line(message.Text)
