extends ItemUse
class_name RepairKitUse

## Using a repair kit asks which damaged item it mends; the inventory sends the use once one is picked.
##
## The GDScript half of net.bestia.zone.item.repair.RepairKitScript.


func on_item_used(_item: ItemResource) -> void:
	var inventory := (Engine.get_main_loop() as SceneTree).get_first_node_in_group("inventory") as Inventory
	if inventory == null:
		printerr("RepairKitUse: no inventory to pick the item from")
		return

	inventory.ask_to_repair(player_item_id)
