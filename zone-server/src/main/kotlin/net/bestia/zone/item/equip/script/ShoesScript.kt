package net.bestia.zone.item.equip.script

import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.item.equip.EquipmentSlot
import org.springframework.stereotype.Component

/** Referenced by `items.yml` id 4 (`shoes`) - a flat agility bonus that grows with the upgrade level. */
@Component
class ShoesScript : EquipmentScript {

  override fun apply(context: StatusValueRecalcContext, slot: EquipmentSlot, upgradeLevel: Int) {
    context.agility += 2 + upgradeLevel
  }
}
