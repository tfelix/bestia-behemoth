package net.bestia.zone.casting

import net.bestia.zone.battle.ecs.status.IsStatusValueDirty
import net.bestia.zone.ecs.core.World
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.ecs.Equipment
import net.bestia.zone.item.ecs.EquipmentChanges
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.ecs.ItemOwner
import net.bestia.zone.item.equip.EquipmentSlot
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * What a strip or a corroding blow does to worn gear: takes it off into the bag, or eats its durability. The world
 * changes at once and the database follows on the owner's queue, the way `UnequipItemHandler` and
 * `LightSourceSystem` keep the two in step.
 */
@Component
class GearOperations(
  private val equipmentChanges: EquipmentChanges,
  private val inventoryService: InventoryService,
  private val asyncJobExecutor: AsyncJobExecutor,
) {

  /** Returns false when nothing was worn there, which a skill against a wild bestia always finds. */
  fun strip(world: World, entityId: EntityId, slot: EquipmentSlot): Boolean {
    val equipment = world.get(entityId, Equipment::class) ?: return false
    equipmentChanges.takeOff(equipment, world.get(entityId, Inventory::class), slot) ?: return false
    world.add(entityId, IsStatusValueDirty)

    val owner = ItemOwner.of(world, entityId) ?: return true
    asyncJobExecutor.submit(key = owner.masterId) { inventoryService.unequip(owner.masterId, owner.playerBestiaId, slot) }

    return true
  }

  /** Takes [percent] of the maximum off the durability of the weapon and the armour worn. */
  fun wear(world: World, entityId: EntityId, percent: Int): Boolean {
    val equipment = world.get(entityId, Equipment::class) ?: return false
    val owner = ItemOwner.of(world, entityId)

    val worn = WORN_SLOTS.mapNotNull { slot ->
      val item = equipment.get(slot)?.takeIf { it.maxDurability > 0 && it.durability > 0 } ?: return@mapNotNull null
      val loss = (item.maxDurability * percent / 100).coerceAtLeast(1)
      item.copy(durability = (item.durability - loss).coerceAtLeast(0)).also { equipment.replace(slot, it) }
    }

    if (owner != null) {
      asyncJobExecutor.submit(key = owner.masterId) {
        worn.forEach { inventoryService.recordWear(it.uniqueId, it.durability) }
      }
    }

    return worn.isNotEmpty()
  }

  private companion object {
    val WORN_SLOTS = listOf(EquipmentSlot.RIGHT_HAND, EquipmentSlot.ARMOR)
  }
}
