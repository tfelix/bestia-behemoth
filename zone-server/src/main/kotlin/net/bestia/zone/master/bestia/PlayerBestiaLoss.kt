package net.bestia.zone.master.bestia

import net.bestia.zone.account.persistence.PlayerBestiaRepository
import net.bestia.zone.battle.persistence.StatusEffectPersistenceService
import net.bestia.zone.ecs.core.World
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.postal.parcel.ParcelService
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.util.EntityId
import net.bestia.zone.util.PlayerBestiaId
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate

/**
 * Loses a player bestia for good. It leaves the world at once. Then its bag goes to its master by post, and its row
 * goes with its learned skills, its container and its stored status effects, as for each bestia of a deleted master
 * in [net.bestia.zone.master.MasterDeletionService].
 */
@Service
class PlayerBestiaLoss(
  private val playerBestiaRepository: PlayerBestiaRepository,
  private val inventoryService: InventoryService,
  private val parcels: ParcelService,
  private val statusEffects: StatusEffectPersistenceService,
  private val connections: ConnectionInfoService,
  private val ownedBestias: OwnedBestiasPublisher,
  private val asyncJobExecutor: AsyncJobExecutor,
  private val transactions: TransactionTemplate,
) {

  /** Call it on the tick or inside a world scope. */
  fun lose(world: World, bestiaId: EntityId) {
    val owned = world.get(bestiaId, OwnedBestia::class) ?: return
    val accountId = world.get(bestiaId, Account::class)?.accountId

    world.destroy(bestiaId)
    accountId?.let { connections.unregisterPlayerBestiaEntity(it, owned.masterId, owned.playerBestiaId) }

    // Keyed by the master like the bestia's own saves, so a save queued before cannot bring the row back.
    asyncJobExecutor.submit(key = owned.masterId) {
      transactions.executeWithoutResult { deleteRow(owned.playerBestiaId, bestiaId) }
      accountId?.let(ownedBestias::publish)
    }
  }

  private fun deleteRow(playerBestiaId: PlayerBestiaId, entityId: EntityId) {
    val bestia = playerBestiaRepository.findByIdOrNull(playerBestiaId) ?: return
    val bag = bestia.container

    // Worn gear goes too: nobody is left to wear it.
    bag.equipped().keys.forEach { bag.unequip(it) }
    if (bag.slots.isNotEmpty()) {
      parcels.postInstant(bestia.master.id, bestia.name ?: bestia.bestia.identifier) { parcel ->
        inventoryService.moveBetweenEscrows(bag, parcel)
      }
    }

    statusEffects.deleteFor(listOf(entityId))
    playerBestiaRepository.delete(bestia)
  }
}
