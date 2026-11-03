package net.bestia.zone.item.ecs

import net.bestia.zone.battle.GearWear
import net.bestia.zone.ecs.core.World
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.equip.EquipmentSlot
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Wears the weapon in hand down for a fight, the way `LightSourceSystem` burns a torch. A weapon at zero
 * durability stays in hand until it is repaired.
 */
@Component
class EquipmentWear(
  private val inventoryService: InventoryService,
  private val asyncJobExecutor: AsyncJobExecutor,
) : GearWear {

  override fun wearWeapon(world: World, entityId: EntityId, durability: Int) {
    val equipment = world.get(entityId, Equipment::class) ?: return
    val weapon = equipment.get(EquipmentSlot.RIGHT_HAND) ?: return
    if (weapon.maxDurability <= 0 || weapon.durability <= 0) return

    val worn = weapon.copy(durability = (weapon.durability - durability).coerceAtLeast(0))
    equipment.replace(EquipmentSlot.RIGHT_HAND, worn)

    val owner = ItemOwner.of(world, entityId) ?: return
    asyncJobExecutor.submit(key = owner.masterId) { inventoryService.recordWear(worn.uniqueId, worn.durability) }
  }
}
