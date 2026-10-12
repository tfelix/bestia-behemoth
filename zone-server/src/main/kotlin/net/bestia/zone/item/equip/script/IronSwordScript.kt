package net.bestia.zone.item.equip.script

import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.item.equip.EquipmentSlot
import net.bestia.zone.item.stat.ItemStats
import org.springframework.stereotype.Component

/** Referenced by `items.yml` id 19 (`iron_sword`) - what Forge Weapon produces. Refining it adds strength. */
@Component
class IronSwordScript : StatBonusEquipmentScript() {

  override fun apply(context: StatusValueRecalcContext, slot: EquipmentSlot, upgradeLevel: Int, stats: ItemStats) {
    super.apply(context, slot, upgradeLevel, stats)
    context.strength += 2 * upgradeLevel
  }
}
