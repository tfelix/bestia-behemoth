package net.bestia.zone.casting

import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.ecs.status.IsStatusValueDirty
import net.bestia.zone.battle.gear.WeaponType
import net.bestia.zone.battle.status.scripts.GearCoating
import net.bestia.zone.ecs.core.World
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.ecs.Equipment
import net.bestia.zone.item.ecs.EquipmentChanges
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.ecs.ItemOwner
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.item.equip.EquipmentSlot
import net.bestia.zone.item.equip.SlotLock
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * What a strip or a corroding blow does to worn gear: takes it off into the bag, or eats its durability, less under a
 * [GearCoating]. The world changes at once and the database follows on the owner's queue, the way
 * `UnequipItemHandler` and `LightSourceSystem` keep the two in step.
 */
@Component
class GearOperations(
  private val equipmentChanges: EquipmentChanges,
  private val inventoryService: InventoryService,
  private val asyncJobExecutor: AsyncJobExecutor,
  private val templates: ItemTemplateRegistry,
) {

  /** The pieces [entityId] wears that a strip may take, by the slot lock each one gets. */
  fun strippable(world: World, entityId: EntityId): List<SlotLock> {
    val equipment = world.get(entityId, Equipment::class) ?: return emptyList()

    return SlotLock.entries.filter { equipment.get(it.slot) != null }
  }

  /** Whether [entityId] holds a one-handed weapon in its left hand as well, rather than a shield or a tome. */
  fun isDualWielding(world: World, entityId: EntityId): Boolean {
    val leftHand = world.get(entityId, Equipment::class)?.get(EquipmentSlot.LEFT_HAND) ?: return false

    return templates.templateOf(leftHand.itemId)?.weaponType?.hands == WeaponType.Hands.ONE
  }

  /** Returns false when nothing was worn there, which a skill against a wild bestia always finds. */
  fun strip(world: World, entityId: EntityId, slot: EquipmentSlot): Boolean {
    val equipment = world.get(entityId, Equipment::class) ?: return false
    equipmentChanges.takeOff(equipment, world.get(entityId, Inventory::class), slot) ?: return false
    world.add(entityId, IsStatusValueDirty)

    val owner = ItemOwner.of(world, entityId) ?: return true
    asyncJobExecutor.submit(key = owner.masterId) { inventoryService.unequip(owner.masterId, owner.playerBestiaId, slot) }

    return true
  }

  /**
   * Takes [percent] of the maximum off the durability of the weapon and the armour worn. What breaks comes off, as
   * a weapon broken in a fight does in `EquipmentWear`.
   */
  fun wear(world: World, entityId: EntityId, percent: Int): Boolean {
    val equipment = world.get(entityId, Equipment::class) ?: return false
    val owner = ItemOwner.of(world, entityId)
    val keptPercent = GearCoating.wearKeptPercent(world.get(entityId, StatusEffects::class))

    val worn = WORN_SLOTS.mapNotNull { slot ->
      val item = equipment.get(slot)?.takeIf { it.maxDurability > 0 && !it.isBroken } ?: return@mapNotNull null
      val loss = (item.maxDurability * percent / 100).coerceAtLeast(1) * keptPercent / 100
      if (loss <= 0) return@mapNotNull null
      slot to item.copy(durability = (item.durability - loss).coerceAtLeast(0)).also { equipment.replace(slot, it) }
    }

    if (owner != null) {
      asyncJobExecutor.submit(key = owner.masterId) {
        worn.forEach { (_, item) -> inventoryService.recordWear(item.uniqueId, item.durability) }
      }
    }
    worn.filter { (_, item) -> item.isBroken }.forEach { (slot, _) -> strip(world, entityId, slot) }

    return worn.isNotEmpty()
  }

  private companion object {
    val WORN_SLOTS = listOf(EquipmentSlot.RIGHT_HAND, EquipmentSlot.ARMOR)
  }
}
