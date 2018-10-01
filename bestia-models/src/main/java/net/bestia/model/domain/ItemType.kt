package net.bestia.model.domain

/*-
 * Type of an item. It determined how an item can be used.
 *
 * Itemtypes:
 * USABLE:	Can be used the effect depends on the actual item. The effect
 *			is determined by a script.
 * EQUIP:	Can be equiped via a bestia. Such items have an EquipItemInfo
 * 			inside the database for extended information.
 * ETC:		Simple item with no effect. Can just be sold or used up by
 * 			other effects.
 * QUEST:	Special quest item. Can not be sold nor dropped or destroyed.
 * CASTABLE: Item can be used for casting. A map/cast indicator will be
 * 			shown upon usage. The item cast script will then be executed.
 *
 *
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
enum class ItemType {
  USABLE, EQUIP, ETC, QUEST, CASTABLE
}
