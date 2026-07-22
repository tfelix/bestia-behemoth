package net.bestia.zone.battle.status

import net.bestia.zone.item.equip.EquipmentSlot

/**
 * What wearing one item does to its wearer's status values. Registered under its simple class name
 * (see [EquipmentScriptRegistry]) and referenced by `Item.script` for
 * [net.bestia.zone.item.Item.ItemType.EQUIP] items - the same script-name-to-bean pattern as
 * [StatusEffectScript] / [StatusEffectScriptRegistry].
 *
 * Applied by [net.bestia.zone.ecs.battle.effects.StatusValueRecalcSystem] on the tick thread while
 * it rebuilds [net.bestia.zone.ecs.battle.status.StatusValues] from scratch, so implementations
 * must be stateless and must only mutate [StatusValueRecalcContext].
 *
 * Equipment without any stat effect simply has no script at all - unlike a status effect, there is
 * nothing else (duration, stacking) an equip script would have to answer.
 */
interface EquipmentScript {

  /** Mutates [context] to reflect this item being worn in [slot] at [upgradeLevel]. */
  fun apply(context: StatusValueRecalcContext, slot: EquipmentSlot, upgradeLevel: Int)
}
