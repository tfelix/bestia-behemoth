package net.bestia.zone.item.equip.script

import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.item.equip.EquipmentSlot
import org.springframework.stereotype.Component

/** Referenced by `items.yml` id 32 (`novice_shirt`) - part of the kit a master is created holding. */
@Component
class NoviceShirtScript : EquipmentScript {

  override fun apply(context: StatusValueRecalcContext, slot: EquipmentSlot, upgradeLevel: Int) {
    context.addDefense(hardDefense = 3, hardMagicDefense = 3)
  }
}
