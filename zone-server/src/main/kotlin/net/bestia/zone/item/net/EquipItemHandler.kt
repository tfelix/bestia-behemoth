package net.bestia.zone.item.net

import net.bestia.zone.item.stat.ItemStatCatalog
import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.skill.NoviceGate
import net.bestia.zone.battle.ecs.level.Level
import net.bestia.zone.battle.ecs.status.IsStatusValueDirty
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.entity.ecs.DeadActionGuard
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.item.ecs.Equipment
import net.bestia.zone.item.ecs.EquipmentChanges
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.persistence.ItemRepository
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.decoder
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import net.bestia.zone.item.equip.EquipmentService
import net.bestia.zone.item.equip.toOpError

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
  private val equipmentChanges: EquipmentChanges,
  private val noviceGate: NoviceGate,
  private val connectionInfoService: ConnectionInfoService,
  private val inventoryService: InventoryService,
  private val asyncJobExecutor: AsyncJobExecutor,
  private val outMessageProcessor: OutMessageProcessor,
  private val deadActionGuard: DeadActionGuard,
  private val world: WorldView,
  private val itemStats: ItemStatCatalog
) : IoMessageHandler<EquipItemCMSG> {
  override val wire = decoder(MessageCase.EQUIP_ITEM) { accountId, envelope ->
    EquipItemCMSG.fromBnet(accountId, envelope.equipItem)
  }

  override fun handle(msg: EquipItemCMSG): Boolean {
    val activeEntityId = connectionInfoService.getActiveEntityId(msg.playerId)
    if (world.read { deadActionGuard.refuses(this, activeEntityId, "equip an item") }) {
      return true
    }

    val heldItemId = world.read { get(activeEntityId, Inventory::class)?.byPlayerItemId(msg.playerItemId)?.itemId }
    val item = heldItemId?.let { itemRepository.findByIdOrNull(it) }
    if (item == null) {
      LOG.warn { "Entity $activeEntityId holds no item ${msg.playerItemId} it knows to equip" }
      sendDenial(msg.playerId, EquipmentService.Denial.ITEM_NOT_FOUND)
      return true
    }

    val applied = world.modify(activeEntityId) { id ->
      val equipment = get(id, Equipment::class)
      val inventory = get(id, Inventory::class)

      if (equipment == null || inventory == null) {
        LOG.warn { "Entity $activeEntityId has no Equipment/Inventory component but tried to equip" }
        return@modify null
      }

      // Looked up again under the lock: it may have left the bag or been put on since it was first read.
      val held = inventory.byPlayerItemId(msg.playerItemId)
        ?.takeIf { it.itemId == item.id && !equipment.isWorn(it.uniqueId) }
      if (held == null) {
        equipment.markDirty()
        return@modify Applied(denial = EquipmentService.Denial.ITEM_NOT_FOUND)
      }

      val itemLevel = held.itemLevel.takeIf { it > 0 } ?: itemStats.itemLevelOf(item.id)
      val denial = equipmentService.checkEquip(
        equipment = equipment,
        inventory = inventory,
        item = item,
        itemLevel = itemLevel,
        slot = msg.slot,
        heldUniqueId = held.uniqueId,
        // Zero for an entity with no Level component at all, which the service reads as unqualified rather
        // than as exempt.
        wearerLevel = get(id, Level::class)?.level ?: 0,
        wearerIsNovice = noviceGate.isNovice(this, id)
      )

      if (denial != null) {
        // Force a re-send of the unchanged authoritative state - this is what keeps the client in
        // sync after it optimistically rendered the item into the slot.
        equipment.markDirty()
        return@modify Applied(denial = denial)
      }

      val previous = equipmentChanges.takeOff(equipment, inventory, msg.slot)
      val worn = equipmentChanges.putOn(equipment, inventory, msg.slot, held, itemLevel)

      if (worn == null) {
        // Should be unreachable after checkEquip; restore whatever was there and refuse.
        previous?.let { equipmentChanges.restore(equipment, inventory, msg.slot, it) }
        equipment.markDirty()
        return@modify Applied(denial = EquipmentService.Denial.NOT_ALLOWED)
      }

      // Gear feeds StatusValueRecalcSystem, so the derived values have to be rebuilt next tick.
      add(id, IsStatusValueDirty)

      Applied(itemId = item.id, uniqueId = worn.uniqueId, replacedUniqueId = previous?.uniqueId)
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
      inventoryService.equip(masterId, playerBestiaId, applied.itemId, applied.uniqueId, msg.slot)
    }
  }

  private fun sendDenial(playerId: Long, denial: EquipmentService.Denial) {
    outMessageProcessor.sendToPlayer(playerId, OperationErrorSMSG(denial.toOpError()))
  }

  private data class Applied(
    val itemId: Long = 0L,
    val uniqueId: Long = 0L,
    val replacedUniqueId: Long? = null,
    val denial: EquipmentService.Denial? = null
  )

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
