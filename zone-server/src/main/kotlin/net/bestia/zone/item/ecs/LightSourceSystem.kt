package net.bestia.zone.item.ecs

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.equip.EquipmentSlot
import net.bestia.zone.item.equip.LightSourceRules
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.time.BestiaClock
import org.springframework.stereotype.Component as SpringComponent

/**
 * Burns down the light worn in [EquipmentSlot.LIGHT_SOURCE] while it is dark, one point of wear per run.
 *
 * A light that burns out is lit again from the bag without a click: a partly burnt torch first, so the bag
 * does not fill up with stubs, then a fresh one off the pile. Only when none is left is the player told.
 */
@SpringComponent
class LightSourceSystem(
  private val clock: BestiaClock,
  private val equipmentChanges: EquipmentChanges,
  private val inventoryService: InventoryService,
  private val asyncJobExecutor: AsyncJobExecutor,
  private val outMessageProcessor: OutMessageProcessor,
) : System {

  override val phase = Phase.UPKEEP
  override val schedule: Schedule = Schedule.EverySeconds(LightSourceRules.BURN_INTERVAL_SECONDS)
  override val reads: ComponentClassSet = setOf(Account::class, Master::class, OwnedBestia::class)
  override val writes: ComponentClassSet = setOf(Equipment::class, Inventory::class)

  override fun update(world: World, deltaTime: Float) {
    if (!LightSourceRules.isLit(clock.now())) {
      return
    }

    world.query(Equipment::class).each { id ->
      val light = get<Equipment>().get(EquipmentSlot.LIGHT_SOURCE) ?: return@each
      if (light.maxDurability <= 0) return@each

      val burnt = light.copy(durability = light.durability - 1)
      if (burnt.durability > 0) {
        get<Equipment>().replace(EquipmentSlot.LIGHT_SOURCE, burnt)
        persistWear(world, id, burnt)
      } else {
        burnOut(world, id, burnt)
      }
    }
  }

  private fun persistWear(world: World, entityId: EntityId, light: Equipment.EquippedItem) {
    val owner = ItemOwner.of(world, entityId) ?: return
    asyncJobExecutor.submit(key = owner.masterId) {
      inventoryService.recordWear(light.uniqueId, light.durability)
    }
  }

  private fun burnOut(world: World, entityId: EntityId, light: Equipment.EquippedItem) {
    val equipment = world.get(entityId, Equipment::class) ?: return
    val inventory = world.get(entityId, Inventory::class) ?: return

    equipmentChanges.takeOff(equipment, inventory, EquipmentSlot.LIGHT_SOURCE)
    inventory.removeByUniqueId(light.uniqueId)

    val spare = spareOf(inventory, light.itemId)
    val relitFromPile = spare?.isStackable == true
    val relit = spare?.let { equipmentChanges.putOn(equipment, inventory, EquipmentSlot.LIGHT_SOURCE, it, light.itemLevel) }

    val owner = ItemOwner.of(world, entityId)
    if (owner != null) {
      asyncJobExecutor.submit(key = owner.masterId) {
        inventoryService.burnOut(owner.masterId, owner.playerBestiaId, EquipmentSlot.LIGHT_SOURCE)
        when {
          relit == null -> Unit
          relitFromPile -> inventoryService.equipFromPile(
            owner.masterId, owner.playerBestiaId, relit.itemId, relit.provenance, relit.uniqueId, EquipmentSlot.LIGHT_SOURCE
          )
          else -> inventoryService.equip(owner.masterId, owner.playerBestiaId, relit.itemId, relit.uniqueId, EquipmentSlot.LIGHT_SOURCE)
        }
      }
    }

    if (relit == null) {
      LOG.debug { "Entity $entityId burnt its last light ${light.itemId}" }
      world.get(entityId, Account::class)?.let {
        outMessageProcessor.sendToPlayer(it.accountId, OperationErrorSMSG(OpError.LIGHT_SOURCE_BURNT_OUT))
      }
    }
  }

  /** The partly burnt copy closest to its end, else the pile. Same kind only: a torch is not relit as a lamp. */
  private fun spareOf(inventory: Inventory, itemId: Long): Inventory.Item? {
    val spares = inventory.getItems().filter { it.itemId == itemId && !it.equipped }

    return spares.filter { !it.isStackable }.minByOrNull { it.durability }
      ?: spares.firstOrNull { it.isStackable }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
