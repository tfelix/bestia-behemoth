package net.bestia.zone.item.equip.script

import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.item.equip.EquipmentSlot
import org.springframework.stereotype.Component

/**
 * Referenced by `items.yml` id 20 (`iron_cuirass`) - what Forge Armor produces.
 *
 * Costs agility, which no other piece of gear does: a full breastplate is the first item heavy enough
 * that wearing it should be a decision rather than an upgrade.
 */
@Component
class IronCuirassScript : EquipmentScript {

  override fun apply(context: StatusValueRecalcContext, slot: EquipmentSlot, upgradeLevel: Int) {
    context.vitality += 5 + 2 * upgradeLevel
    context.agility -= 2
  }
}
