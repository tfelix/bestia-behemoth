extends ItemUse
class_name DigToolUse

## A shovel or a pickaxe: the player picks a tile and digs there.
##
## The GDScript half of net.bestia.zone.treasure.DigService, which digs with whatever the right hand holds. A
## tool still in the bag goes into the hand first, in the same breath. A refusal comes back as an OperationError.

const _MARKER_SCENE := preload("res://Game/VFX/AOECastIndicator/AOECastIndicator.tscn")

## How far the trench reaches around the spot, in tiles. The server's carve is this wide.
const _TRENCH_RADIUS := 2.0


func on_item_used(item: ItemResource) -> void:
	var mouse := MouseManager.get_instance()
	if mouse == null:
		printerr("DigToolUse: no MouseManager to dig with %s" % [item.name_key])
		return

	var marker = _MARKER_SCENE.instantiate()
	marker.set_radius(_TRENCH_RADIUS)
	mouse.enter_item_targeting(item, self, null, marker)


func on_targeting_click(_item: ItemResource, click_info: Dictionary) -> bool:
	if not ConnectionManager.is_ready_to_send():
		return true

	var inventory = Engine.get_main_loop().get_first_node_in_group("inventory")
	if inventory != null and not inventory.is_worn(player_item_id):
		ConnectionManager.equip_item(player_item_id, EquipmentSlot.Slot.RIGHT_HAND)
	ConnectionManager.dig(click_info["tile"])

	return true
