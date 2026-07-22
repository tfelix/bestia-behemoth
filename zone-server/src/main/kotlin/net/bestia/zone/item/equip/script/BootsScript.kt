package net.bestia.zone.item.equip.script

import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.item.equip.EquipmentSlot
import org.springframework.stereotype.Component

/** Referenced by `items.yml` id 5 (`boots`) - a flat vitality bonus that grows with the upgrade level. */
@Component
class BootsScript : EquipmentScript {

  override fun apply(context: StatusValueRecalcContext, slot: EquipmentSlot, upgradeLevel: Int) {
    context.vitality += 2 + upgradeLevel
  }
}
