package net.bestia.zone.item.equip.script

import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.item.equip.EquipmentSlot
import net.bestia.zone.item.stat.ItemStats
import org.springframework.stereotype.Component

/**
 * The script of every item that names none: it applies the item's [ItemStats]. A script with special behaviour
 * extends this and calls `super.apply`, so the stats in `items.yml` stay the one place plain bonuses live.
 */
@Component
open class StatBonusEquipmentScript : EquipmentScript {

  override fun apply(context: StatusValueRecalcContext, slot: EquipmentSlot, upgradeLevel: Int, stats: ItemStats) {
    stats.applyTo(context)
  }
}
