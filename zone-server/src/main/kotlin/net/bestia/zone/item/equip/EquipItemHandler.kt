package net.bestia.zone.item.equip

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.OperationErrorProto
import net.bestia.zone.ecs.battle.status.IsStatusValueDirty
import net.bestia.zone.ecs.core.AsyncJobExecutor
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.ecs.item.Equipment
import net.bestia.zone.ecs.item.Inventory
import net.bestia.zone.item.ItemRepository
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.message.InMessageProcessor
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

/**
 * Wears an item the player already holds. The server is the authority throughout: it resolves the
 * acting entity from the session (never from the message), asks [EquipmentService] for permission,
 * and on refusal re-pushes the untouched [Equipment] component so a client that already moved the
 * icon locally snaps back instead of silently drifting out of sync.
 *
 * Equipping over an occupied slot swaps: the old item goes back to being a plain inventory item,
 * matching what players expect from Ragnarok Online.
 */
@Component
class EquipItemHandler(
  private val itemRepository: ItemRepository,
  private val equipmentService: EquipmentService,
  private val connectionInfoService: ConnectionInfoService,
  private val inventoryService: InventoryService,
  private val asyncJobExecutor: AsyncJobExecutor,
  private val outMessageProcessor: OutMessageProcessor,
  private val world: WorldView
) : InMessageProcessor.IncomingMessageHandler<EquipItemCMSG> {
  override val handles = EquipItemCMSG::class

  override fun handle(msg: EquipItemCMSG): Boolean {
    LOG.trace { "RX: $msg" }

    val item = itemRepository.findByIdOrNull(msg.itemId)
    if (item == null) {
      LOG.warn { "Item ${msg.itemId} was not found in the database" }
      sendDenial(msg.playerId, EquipmentService.Denial.ITEM_NOT_FOUND)
      return true
    }

    val activeEntityId = connectionInfoService.getActiveEntityId(msg.playerId)

    val applied = world.modify(activeEntityId) { id ->
      val equipment = get(id, Equipment::class)
      val inventory = get(id, Inventory::class)

      if (equipment == null || inventory == null) {
        LOG.warn { "Entity $activeEntityId has no Equipment/Inventory component but tried to equip" }
        return@modify null
      }

      // Prefer the instance the client named; fall back to any held copy for the case where the
      // client does not know the instance id yet (see Inventory.Item's KDoc).
      val held = inventory.getItems()
        .filter { it.itemId == item.id && !equipment.isWorn(it.uniqueId) }
        .firstOrNull { msg.uniqueId == 0L || it.uniqueId == msg.uniqueId }

      val denial = equipmentService.checkEquip(
        equipment = equipment,
        inventory = inventory,
        item = item,
        slot = msg.slot,
        heldUniqueId = held?.uniqueId ?: msg.uniqueId
      )

      if (denial != null) {
        // Force a re-send of the unchanged authoritative state - this is what keeps the client in
        // sync after it optimistically rendered the item into the slot.
        equipment.markDirty()
        return@modify Applied(denial = denial)
      }

      val previous = equipment.unequip(msg.slot)
      val equipped = equipment.equip(
        msg.slot,
        Equipment.EquippedItem(itemId = item.id, uniqueId = held?.uniqueId ?: 0L)
      )

      if (!equipped) {
        // Should be unreachable after checkEquip; restore whatever was there and refuse.
        previous?.let { equipment.equip(msg.slot, it) }
        equipment.markDirty()
        return@modify Applied(denial = EquipmentService.Denial.NOT_ALLOWED)
      }

      // Gear feeds StatusValueRecalcSystem, so the derived values have to be rebuilt next tick.
      add(id, IsStatusValueDirty)

      // Keep the inventory view's equipped marker in step with what actually got worn/swapped out.
      previous?.let { inventory.setEquipped(it.uniqueId, false) }
      inventory.setEquipped(held?.uniqueId ?: 0L, true)

      Applied(uniqueId = held?.uniqueId ?: 0L, replacedUniqueId = previous?.uniqueId)
    }

    if (applied == null) {
      sendDenial(msg.playerId, EquipmentService.Denial.ITEM_NOT_FOUND)
      return true
    }

    if (applied.denial != null) {
      LOG.debug { "Account ${msg.playerId} may not equip ${item.identifier} in ${msg.slot}: ${applied.denial}" }
      sendDenial(msg.playerId, applied.denial)
      return true
    }

    schedulePersist(msg, applied)

    return true
  }

  /** Write-behind of the durable equipped flag, off the tick thread (same shape as UseItemHandler). */
  private fun schedulePersist(msg: EquipItemCMSG, applied: Applied) {
    val masterId = connectionInfoService.getMasterId(msg.playerId)
    val playerBestiaId = connectionInfoService.getActivePlayerBestiaId(msg.playerId)

    asyncJobExecutor.submit(key = masterId) {
      // Unconditional: ItemContainer.equip refuses an occupied slot, and clearing an already empty
      // slot is a no-op, so this covers the swap case without a second branch.
      inventoryService.unequip(masterId, playerBestiaId, msg.slot)
      inventoryService.equip(masterId, playerBestiaId, msg.itemId, applied.uniqueId, msg.slot)
    }
  }

  private fun sendDenial(playerId: Long, denial: EquipmentService.Denial) {
    val code = when (denial) {
      EquipmentService.Denial.SLOT_NOT_AVAILABLE -> OperationErrorProto.OpError.EQUIP_SLOT_NOT_AVAILABLE
      EquipmentService.Denial.ITEM_NOT_FOUND -> OperationErrorProto.OpError.EQUIP_ITEM_NOT_FOUND
      EquipmentService.Denial.NOT_ALLOWED -> OperationErrorProto.OpError.EQUIP_NOT_ALLOWED
    }

    outMessageProcessor.sendToPlayer(playerId, OperationErrorSMSG(code))
  }

  private data class Applied(
    val uniqueId: Long = 0L,
    val replacedUniqueId: Long? = null,
    val denial: EquipmentService.Denial? = null
  )

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
