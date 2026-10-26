extends Control
class_name GroundDropZone

## Fallback drop target covering the whole game screen. Anything dragged from
## the inventory that isn't dropped onto a more specific target (a shortcut
## slot, an inventory slot, ...) lands here and gets discarded on the ground.
## Kept separate from Inventory's own full-rect root so that root can stay a
## pure layout wrapper (mouse_filter = IGNORE) instead of also acting as an
## input-catching layer, which previously made it win the drag/drop hit test
## ahead of the Shortcuts bar.

var DropAmountDialogScn = preload("res://Game/UI/Inventory/DropAmountDialog/DropAmountDialog.tscn")

@export var inventory: Inventory = null


func _drop_data(_at_position: Vector2, data: Variant) -> void:
	var item_id: int = data.get("id")
	# The pile or copy that was actually dragged. Two charts of one template are two different maps, and two
	# piles of one potion can come from two makers.
	var player_item_id: int = data.get("player_item_id", 0)
	var owned_amount: int = data.get("amount", 0)
	if owned_amount <= 0:
		return

	if owned_amount == 1:
		ConnectionManager.drop_item(player_item_id, 1)
		return

	var item_resource = ItemDB.get_instance().get_item(item_id)
	var dialog = DropAmountDialogScn.instantiate() as DropAmountDialog
	add_child(dialog)
	dialog.amount_confirmed.connect(func(_id: int, amount: int) -> void: ConnectionManager.drop_item(player_item_id, amount))
	dialog.confirmed.connect(dialog.queue_free)
	dialog.canceled.connect(dialog.queue_free)
	dialog.open_for(item_resource, owned_amount)


func _can_drop_data(_at_position: Vector2, data: Variant) -> bool:
	return typeof(data) == TYPE_DICTIONARY and data.get("source") == "inventory_item"
