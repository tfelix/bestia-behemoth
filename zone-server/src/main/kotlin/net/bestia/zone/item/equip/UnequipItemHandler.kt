package net.bestia.zone.item.equip

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.battle.ecs.status.IsStatusValueDirty
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.modify
import net.bestia.zone.entity.ecs.DeadActionGuard
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.item.ecs.Equipment
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.message.TickMessageHandler
import net.bestia.zone.message.decoder
import org.springframework.stereotype.Component

/**
 * Takes an item off. The item never left the owner's container (only its
 * [net.bestia.zone.item.container.ContainerSlot.equippedIn] marker is cleared), so there is nothing
 * to give back - it simply becomes a plain inventory item again.
 *
 * Unequipping is always permitted; unlike equipping there is no rule to consult, so no denial path.
 */
@Component
class UnequipItemHandler(
  private val connectionInfoService: ConnectionInfoService,
  private val inventoryService: InventoryService,
  private val asyncJobExecutor: AsyncJobExecutor,
  private val deadActionGuard: DeadActionGuard,
) : TickMessageHandler<UnequipItemCMSG> {
  override val wire = decoder(MessageCase.UNEQUIP_ITEM) { accountId, envelope ->
    UnequipItemCMSG.fromBnet(accountId, envelope.unequipItem)
  }

  override fun handle(world: World, msg: UnequipItemCMSG): Boolean {
    val activeEntityId = connectionInfoService.getActiveEntityId(msg.playerId)
    if (deadActionGuard.refuses(world, activeEntityId, "unequip an item")) {
      return true
    }

    val removed = world.modify(activeEntityId) { id ->
      val equipment = get(id, Equipment::class)

      if (equipment == null) {
        LOG.warn { "Entity $activeEntityId has no Equipment component but tried to unequip" }
        return@modify null
      }

      val removed = equipment.unequip(msg.slot) ?: return@modify null

      // Losing gear changes the derived values just as gaining it does.
      add(id, IsStatusValueDirty)

      get(id, Inventory::class)?.setEquipped(removed.uniqueId, false)

      removed
    }

    if (removed == null) {
      LOG.debug { "Account ${msg.playerId} tried to unequip empty slot ${msg.slot}" }
      return true
    }

    val masterId = connectionInfoService.getMasterId(msg.playerId)
    val playerBestiaId = connectionInfoService.getActivePlayerBestiaId(msg.playerId)

    asyncJobExecutor.submit(key = masterId) {
      inventoryService.unequip(masterId, playerBestiaId, msg.slot)
    }

    return true
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
