extends Object
class_name ItemUse

## Function which gets called if an item is used. It will either immediatly
## send the usage request to the server or, depending on the item, first
## triggers some feedback to the user to gather more data e.g. positioning.
@warning_ignore("unused_parameter")
func on_item_used(item: ItemResource) -> void:
	pass
