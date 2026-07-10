extends Object
class_name ItemUse

## Function which gets called if an item is used. It will either immediatly
## send the usage request to the server or, depending on the item, first
## triggers some feedback to the user to gather more data e.g. positioning.
@warning_ignore("unused_parameter")
func on_item_used(item: ItemResource) -> void:
	pass


## Called by MouseManager's item-targeting state on the next world click,
## once this item has entered targeting mode via MouseManager.enter_item_targeting.
## click_info holds "position" (Vector3) and "target" (Node3D or null for a
## ground click). Return true once the interaction is resolved to return to
## the default mouse mode, or false to keep waiting for another click.
@warning_ignore("unused_parameter")
func on_targeting_click(item: ItemResource, click_info: Dictionary) -> bool:
	return true


## Called when targeting is cancelled (Escape or a clean right-click) before
## a click was confirmed.
@warning_ignore("unused_parameter")
func on_targeting_cancelled(item: ItemResource) -> void:
	pass
