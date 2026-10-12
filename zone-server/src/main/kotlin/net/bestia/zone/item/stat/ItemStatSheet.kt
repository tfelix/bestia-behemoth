package net.bestia.zone.item.stat

import net.bestia.zone.battle.status.StatusValueRecalcContext

/**
 * What an item is authored with in `items.yml`, apart from the catalogue facts on
 * [net.bestia.zone.item.persistence.Item].
 *
 * The four base properties make a weapon a weapon and armor armor. They are what [baseLevel] already stands
 * for, so they never raise the item level. [stats] are bonuses on top, and they do.
 */
data class ItemStatSheet(
  val baseLevel: Int = 1,
  val attack: Int = 0,
  val magicAttack: Int = 0,
  val defense: Int = 0,
  val magicDefense: Int = 0,
  val stats: Map<ItemStat, Int> = emptyMap(),
) {

  fun applyBasePropertiesTo(context: StatusValueRecalcContext) {
    context.addAttack(atk = attack, matk = magicAttack)
    context.addDefense(hardDefense = defense, hardMagicDefense = magicDefense)
  }

  companion object {
    val NONE = ItemStatSheet()
  }
}
