extends Object
class_name Item

# Function which gets called if an item is used. It will either immediatly
# send the usage request to the server or, depending on the item, first
# triggers some feedback to the user to gather more data e.g. positioning.
func _on_used() -> void:
	pass
