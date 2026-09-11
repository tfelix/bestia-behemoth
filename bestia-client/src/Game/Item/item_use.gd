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
## Return true once the interaction is resolved to return to the default mouse mode, or false to keep
## waiting for another click.
##
## click_info holds:
## - "position" (Vector3): the raw world point that was clicked.
## - "tile" (Vector3): the tile containing it. [b]This is what goes on the wire[/b] - Vec3Convert rounds,
##   so handing it "position" puts half of every tile onto its neighbour. See TileSpace.
## - "yaw" (float): which way a placement ghost was turned, in radians. 0 when there is no ghost.
## - "target" (Node3D or null for a ground click): whatever was clicked, and that is a wider set than it
##   looks - a BestiaVisual, an ItemVisual, or a PropPicker (the click target of a collectible crystal or
##   shard). Check the type before reaching into it rather than assuming a click landed on a creature.
@warning_ignore("unused_parameter")
func on_targeting_click(item: ItemResource, click_info: Dictionary) -> bool:
	return true


## Called when targeting is cancelled (Escape or a clean right-click) before
## a click was confirmed.
@warning_ignore("unused_parameter")
func on_targeting_cancelled(item: ItemResource) -> void:
	pass
