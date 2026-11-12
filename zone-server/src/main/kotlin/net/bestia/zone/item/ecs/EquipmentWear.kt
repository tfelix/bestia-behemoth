package net.bestia.zone.item.ecs

import net.bestia.zone.battle.GearWear
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.ecs.status.IsStatusValueDirty
import net.bestia.zone.battle.status.scripts.GearCoating
import net.bestia.zone.ecs.core.World
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.equip.EquipmentSlot
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Wears the weapon in hand down for a fight, the way `LightSourceSystem` burns a torch, less under a [GearCoating]. A
 * weapon worn to zero durability falls out of the hand, and `EquipmentService` refuses it until it is repaired.
 */
@Component
class EquipmentWear(
  private val equipmentChanges: EquipmentChanges,
  private val inventoryService: InventoryService,
  private val asyncJobExecutor: AsyncJobExecutor,
) : GearWear {

  override fun wearWeapon(world: World, entityId: EntityId, durability: Int) {
    val equipment = world.get(entityId, Equipment::class) ?: return
    val weapon = equipment.get(EquipmentSlot.RIGHT_HAND) ?: return
    if (weapon.maxDurability <= 0 || weapon.isBroken) return
    val loss = durability * GearCoating.wearKeptPercent(world.get(entityId, StatusEffects::class)) / 100
    if (loss <= 0) return

    val worn = weapon.copy(durability = (weapon.durability - loss).coerceAtLeast(0))
    equipment.replace(EquipmentSlot.RIGHT_HAND, worn)
    if (worn.isBroken) {
      equipmentChanges.takeOff(equipment, world.get(entityId, Inventory::class), EquipmentSlot.RIGHT_HAND)
      world.add(entityId, IsStatusValueDirty)
    }

    val owner = ItemOwner.of(world, entityId) ?: return
    asyncJobExecutor.submit(key = owner.masterId) {
      inventoryService.recordWear(worn.uniqueId, worn.durability)
      if (worn.isBroken) {
        inventoryService.unequip(owner.masterId, owner.playerBestiaId, EquipmentSlot.RIGHT_HAND)
      }
    }
  }
}
