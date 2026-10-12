package net.bestia.zone.item.equip.script

import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.item.stat.ItemStats
import net.bestia.zone.item.equip.EquipmentSlot

/**
 * What wearing one item does to its wearer. Registered under its simple class name (see
 * [EquipmentScriptRegistry]) and referenced by `Item.script`; an item that names none gets
 * [StatBonusEquipmentScript], which applies the item's [ItemStats] and nothing else.
 *
 * Applied on the tick thread while [net.bestia.zone.battle.ecs.effects.StatusValueRecalcSystem] rebuilds the
 * status values, so implementations must be stateless and only mutate [StatusValueRecalcContext].
 */
interface EquipmentScript {

  fun apply(context: StatusValueRecalcContext, slot: EquipmentSlot, upgradeLevel: Int, stats: ItemStats)
}
