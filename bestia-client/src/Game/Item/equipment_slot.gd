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
}

const COUNT := 9

## Every slot set - what a master has. A bestia only has the subset its species declares.
const ALL_MASK := (1 << COUNT) - 1

## ItemResource.equip_slot stores "slot ordinal + 1" so that 0 can mean "not equipment".
## Converts such a stored value back into a [enum Slot], or -1 when the item is not equipment.
static func from_item_value(item_equip_slot: int) -> int:
	if item_equip_slot <= 0 or item_equip_slot > COUNT:
		return -1
	return item_equip_slot - 1


static func has_slot(mask: int, slot: int) -> bool:
	return (mask & (1 << slot)) != 0


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
		_: return "Unknown"
