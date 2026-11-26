class_name EquipmentSlot

## The equipment slots an entity can wear items in - the classic Ragnarok Online set without its lower head slot.
##
## [b]This enum mirrors[/b] net.bestia.zone.item.equip.EquipmentSlot on the server: the values are
## that enum's ordinals and are what EquipItemCMSG/UnequipItemCMSG and EquipmentComponentSMSG carry
## on the wire. Appending is safe, reordering is not.
enum Slot {
	HEAD_UPPER = 0,
	HEAD_MID = 1,
	ARMOR = 2,
	GARMENT = 3,
	FOOTGEAR = 4,
	RIGHT_HAND = 5,
	LEFT_HAND = 6,
	ACCESSORY_1 = 7,
	ACCESSORY_2 = 8,
	LIGHT_SOURCE = 9,
}

const COUNT := 10

## Every slot set - what a master has. A bestia only has the subset its species declares.
const ALL_MASK := (1 << COUNT) - 1

## ItemResource.equip_slot stores "slot ordinal + 1" so that 0 can mean "not equipment".
## Converts such a stored value back into a [enum Slot], or -1 when the item is not equipment.
static func from_item_value(item_equip_slot: int) -> int:
	if item_equip_slot <= 0 or item_equip_slot > COUNT:
		return -1
	return item_equip_slot - 1


## The weapon types that may go into the left hand as a second weapon. Mirrors WeaponType.isDualWieldable.
const DUAL_WIELDABLE: Array[String] = ["DAGGER", "FIST", "WHIP", "SWORD", "AXE", "MACE", "SPEAR", "WAND"]


## Whether [param item] may go into [param slot]: its own slot, the second accessory slot for an accessory (which
## declares ACCESSORY_1), or the left hand for a second weapon. Mirrors Item.isWornIn; the server also asks for the
## Dual Wield skill.
static func fits(item: ItemResource, slot: int) -> bool:
	var declared := from_item_value(item.equip_slot)
	if declared < 0:
		return false
	if declared == slot:
		return true
	if declared == Slot.ACCESSORY_1 and slot == Slot.ACCESSORY_2:
		return true
	return slot == Slot.LEFT_HAND and item.weapon_type in DUAL_WIELDABLE


static func has_slot(mask: int, slot: int) -> bool:
	return (mask & (1 << slot)) != 0


## Every armor type (CLOTH, LIGHT, MEDIUM, HEAVY) - what a master's body can carry. A bestia only what its species
## declares in [member BestiaResource.armor_types].
const ALL_ARMOR_TYPES := (1 << 4) - 1


## Whether a body that carries the armor types in [param armor_mask] can wear [param item]. Gear with no armor type
## (weapons, accessories) always can. Mirrors Equipment.canWearArmorType on the server: bit n of the mask is the
## server's ArmorType ordinal n, and the item stores that ordinal + 1.
static func wears_armor_type(armor_mask: int, item: ItemResource) -> bool:
	return item.armor_type <= 0 or (armor_mask & (1 << (item.armor_type - 1))) != 0


static func display_name(slot: int) -> String:
	match slot:
		Slot.HEAD_UPPER: return "Upper Head"
		Slot.HEAD_MID: return "Mid Head"
		Slot.ARMOR: return "Armor"
		Slot.GARMENT: return "Garment"
		Slot.FOOTGEAR: return "Footgear"
		Slot.RIGHT_HAND: return "Right Hand"
		Slot.LEFT_HAND: return "Left Hand"
		Slot.ACCESSORY_1: return "Accessory 1"
		Slot.ACCESSORY_2: return "Accessory 2"
		Slot.LIGHT_SOURCE: return "Light Source"
		_: return "Unknown"
