extends Node
class_name ShortcutScript

# Base class for custom shortcut scripts
# Override the execute method to implement custom behavior


func execute(shortcut_data: ShortcutData) -> void:
	# Implement custom behavior here
	# Access shortcut_data.type and shortcut_data.reference_id
	push_warning("ShortcutScript.execute() not implemented")


# Helper method to get item from ItemDB
func get_item(item_id: int) -> ItemResource:
	return ItemDB.get_instance().get_item(item_id)


# Helper method to send messages to server
func send_to_server(message) -> void:
	var bnet_socket = Engine.get_singleton("BnetSocket")
	if bnet_socket:
		bnet_socket.send_message(message)
	else:
		push_error("BnetSocket not found")
