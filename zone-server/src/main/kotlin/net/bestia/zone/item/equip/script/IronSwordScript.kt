package net.bestia.zone.item.equip.script

import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.item.equip.EquipmentSlot
import org.springframework.stereotype.Component

/** Referenced by `items.yml` id 19 (`iron_sword`) - what Forge Weapon produces. */
@Component
class IronSwordScript : EquipmentScript {

  override fun apply(context: StatusValueRecalcContext, slot: EquipmentSlot, upgradeLevel: Int) {
    context.strength += 4 + 2 * upgradeLevel
    context.dexterity += 1
  }
}
