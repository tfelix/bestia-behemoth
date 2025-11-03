package net.bestia.zone.bestia

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.util.PlayerBestiaId
import net.bestia.zone.ecs.session.ConnectionInfoService
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.movement.Speed
import net.bestia.zone.ecs.status.Level
import net.bestia.zone.ecs.visual.BestiaVisual
import net.bestia.zone.ecs.ZoneServer
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PlayerBestiaEntityFactory(
  private val playerBestiaRepository: PlayerBestiaRepository,
  private val zoneServer: ZoneServer,
  private val connectionInfoService: ConnectionInfoService
) {

  /**
   * Spawns the given player bestia into the world.
   * It makes sure the same bestia can never be spawned twice.
   */
  @Transactional(readOnly = true)
  fun createPlayerBestiaEntity(
    playerBestiaId: PlayerBestiaId,
  ) {
    val playerBestia = playerBestiaRepository.findByIdOrThrow(playerBestiaId)

    createPlayerBestiaEntity(playerBestia)
  }

  fun createPlayerBestiaEntity(
    playerBestia: PlayerBestia,
  ) {
    // spawn the entity into the world
    val entityId = zoneServer.addEntityWithWriteLock { entity ->
      entity.addAll(
        Position.fromVec3(playerBestia.position),
        Level(playerBestia.level),
        Speed(),
        BestiaVisual(playerBestia.bestia.id.toInt())
      )
    }

    val accountId = playerBestia.master.account.id
    val playerBestiaId = playerBestia.id
    val masterId = playerBestia.master.id

    LOG.info { "Spawned player bestia $playerBestiaId for account $accountId with entity id: $entityId" }

    connectionInfoService.registerPlayerBestiaEntity(
      accountId = accountId,
      masterId = masterId,
      playerBestiaId = playerBestiaId,
      playerBestiaEntityId = entityId
    )
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}

