extends ShortcutScript
class_name PotionUseScript

# Example custom script for using potions with special effects
# This demonstrates how to extend ShortcutScript for specialized behavior


func execute(shortcut_data: ShortcutData) -> void:
	if shortcut_data.type != ShortcutData.ShortcutType.ITEM:
		return

	var item = get_item(shortcut_data.reference_id)
	if not item:
		push_error("Item not found: ", shortcut_data.reference_id)
		return

	# Example: Check if it's a health potion
	if "potion" in item.name.to_lower():
		print("Using potion with special effect: ", item.name)
		# Play sound effect
		# Show visual effect
		# Send to server
		_use_potion(shortcut_data.reference_id)
	else:
		# Default item usage
		_use_item_default(shortcut_data.reference_id)


func _use_potion(item_id: int) -> void:
	# TODO: Add potion-specific logic
	# - Play drinking animation
	# - Show particle effects
	# - Send message to server
	print("Drinking potion: ", item_id)


func _use_item_default(item_id: int) -> void:
	# Default item usage
	print("Using item: ", item_id)
