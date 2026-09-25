package net.bestia.zone.item.equip.script

import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.item.equip.EquipmentSlot
import org.springframework.stereotype.Component

/** Referenced by `items.yml` id 33 (`novice_boots`) - part of the kit a master is created holding. */
@Component
class NoviceBootsScript : EquipmentScript {

  override fun apply(context: StatusValueRecalcContext, slot: EquipmentSlot, upgradeLevel: Int) {
    context.addDefense(hardDefense = 2, hardMagicDefense = 2)
  }
}
