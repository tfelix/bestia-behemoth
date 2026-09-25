package net.bestia.zone.item.equip.script

import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.item.equip.EquipmentSlot
import org.springframework.stereotype.Component

/**
 * Referenced by `items.yml` id 31 (`novice_knife`) - part of the kit a master is created holding.
 *
 * Flat attack rather than the strength `IronSwordScript` grants: a novice's first weapon should read as a
 * weapon, and strength would also quietly move their carry limit and their soft defence.
 */
@Component
class NoviceKnifeScript : EquipmentScript {

  override fun apply(context: StatusValueRecalcContext, slot: EquipmentSlot, upgradeLevel: Int) {
    context.addAttack(atk = 10)
  }
}
