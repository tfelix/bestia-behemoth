class_name Angler
extends RefCounted

## Decides when a ground click is a cast rather than a walk. The server checks the cast again; this only
## keeps a click on water from turning into a swim for a master with a rod in hand.

## The rods in zone-server's fishing.yml, by item name key.
const ROD_NAME_KEYS: Array[StringName] = [&"WILLOW_ROD", &"BAMBOO_ROD", &"ASH_ROD", &"STEEL_ROD"]

## fishing.yml's reach-metres. Water further away is walked to instead.
const REACH_METRES := 8.0


static func holds_rod(entity: Entity) -> bool:
	var held: Variant = entity.get_equipment().get(EquipmentSlot.Slot.RIGHT_HAND)
	if held == null:
		return false

	var item := ItemDB.get_instance().get_item(held["item_id"])
	return item != null and StringName(item.name_key) in ROD_NAME_KEYS


## Casts onto the water a ground click landed under, and answers whether it did.
static func try_cast(click_position: Vector3) -> bool:
	var entity_manager := EntityManager.get_instance()
	var master := entity_manager.get_master_entity()
	if master == null or master != entity_manager.get_controlled_entity() or not holds_rod(master):
		return false

	var water_top: float = ConnectionManager.chunk_stream.WaterTopYAt(click_position.x, click_position.z, click_position.y)
	if is_nan(water_top):
		return false

	var from := Vector2(master.global_position.x, master.global_position.z)
	if from.distance_to(Vector2(click_position.x, click_position.z)) > REACH_METRES:
		return false

	ConnectionManager.cast_line(Vector3(floorf(click_position.x), water_top, floorf(click_position.z)))
	return true
