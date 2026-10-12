package net.bestia.zone.item.stat

import net.bestia.zone.battle.status.StatusValueRecalcContext

/** Everything one copy of an item adds to its wearer: the sheet it was authored with, and what it rolled. */
data class ItemStats(
  val sheet: ItemStatSheet,
  val rolled: List<StatValue> = emptyList()
) {

  fun applyTo(context: StatusValueRecalcContext) {
    sheet.applyBasePropertiesTo(context)
    sheet.stats.forEach { (stat, value) -> stat.applyTo(context, value) }
    rolled.forEach { it.stat.applyTo(context, it.value) }
  }
}
