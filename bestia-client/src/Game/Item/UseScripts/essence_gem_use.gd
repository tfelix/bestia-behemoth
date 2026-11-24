extends ItemUse
class_name EssenceGemUse

## Using an Essence Gem asks which item it goes into; the inventory sends the use once one is picked.
##
## The GDScript half of net.bestia.zone.item.essence.EssenceGemScript.


func on_item_used(_item: ItemResource) -> void:
	var inventory := (Engine.get_main_loop() as SceneTree).get_first_node_in_group("inventory") as Inventory
	if inventory == null:
		printerr("EssenceGemUse: no inventory to pick the item from")
		return

	inventory.ask_to_socket(player_item_id)
