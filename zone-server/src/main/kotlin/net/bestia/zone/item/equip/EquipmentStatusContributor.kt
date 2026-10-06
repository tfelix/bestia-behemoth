package net.bestia.zone.item.equip

import net.bestia.zone.battle.status.StatusValueContributor
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.item.ecs.Equipment
import net.bestia.zone.item.equip.script.EquipmentScriptRegistry
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

@Component
class EquipmentStatusContributor(
  private val equipmentScriptRegistry: EquipmentScriptRegistry,
) : StatusValueContributor {

  override val reads: ComponentClassSet = setOf(Equipment::class)

  override fun contribute(context: StatusValueRecalcContext, world: World, id: EntityId) {
    val worn = world.get(id, Equipment::class)?.getWorn().orEmpty()
    for ((slot, item) in worn) {
      val script = equipmentScriptRegistry.getByItemId(item.itemId) ?: continue
      script.apply(context, slot, item.upgradeLevel)
    }

    // The damage formula prices refinement for the right hand only.
    context.weaponUpgradeLevel = worn[EquipmentSlot.RIGHT_HAND]?.upgradeLevel ?: 0
  }
}
