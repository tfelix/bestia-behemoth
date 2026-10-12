package net.bestia.zone.item.equip.script

import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.item.equip.EquipmentSlot
import net.bestia.zone.item.stat.ItemStats
import org.springframework.stereotype.Component

/** Referenced by `items.yml` id 20 (`iron_cuirass`) - what Forge Armor produces. Refining it adds vitality. */
@Component
class IronCuirassScript : StatBonusEquipmentScript() {

  override fun apply(context: StatusValueRecalcContext, slot: EquipmentSlot, upgradeLevel: Int, stats: ItemStats) {
    super.apply(context, slot, upgradeLevel, stats)
    context.vitality += 2 * upgradeLevel
  }
}
