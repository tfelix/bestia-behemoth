package net.bestia.zone.master.bestia

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.util.AccountId
import net.bestia.zone.util.EntityId
import net.bestia.zone.util.PlayerBestiaId
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import net.bestia.zone.account.persistence.PlayerBestiaRepository

/**
 * Makes sure every bestia a master owns is in the world and known to the session once that master is selected.
 *
 * A bestia outlives its owner's session in the live world, so after a relog it is only re-registered; after a
 * restart it is spawned again from its row.
 */
@Component
class OwnedBestiaSpawnService(
  private val playerBestiaRepository: PlayerBestiaRepository,
  private val playerBestiaEntitySpawner: PlayerBestiaEntitySpawner,
  private val connectionInfoService: ConnectionInfoService,
  private val world: WorldView,
) {

  /** One transaction, because the spawner walks each row's lazy container and skills. */
  @Transactional(readOnly = true)
  fun bringBack(accountId: AccountId, masterId: Long) {
    val registered = connectionInfoService.getOwnedEntitiesByMaster(accountId, masterId)
      .filter { world.read { isAlive(it.entityId) } }
      .map { it.playerBestiaId }
      .toSet()
    val live = liveOwnedBestias()

    for (playerBestia in playerBestiaRepository.findAllByMasterId(masterId)) {
      if (playerBestia.id in registered) continue

      val entityId = live[playerBestia.id]
      if (entityId != null) {
        connectionInfoService.registerPlayerBestiaEntity(accountId, masterId, playerBestia.id, entityId)
      } else {
        LOG.debug { "Spawning player bestia ${playerBestia.id} of master $masterId" }
        playerBestiaEntitySpawner.spawnPlayerBestia(playerBestia)
      }
    }
  }

  private fun liveOwnedBestias(): Map<PlayerBestiaId, EntityId> {
    return world.read {
      val found = mutableMapOf<PlayerBestiaId, EntityId>()
      query(OwnedBestia::class).each { id -> found[get<OwnedBestia>().playerBestiaId] = id }
      found
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
