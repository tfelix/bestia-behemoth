package net.bestia.zone.account.master

import net.bestia.zone.bestia.PlayerBestiaNotFoundException
import net.bestia.zone.bestia.PlayerBestiaRepository
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.session.ConnectionInfoService
import net.bestia.zone.ecs.status.Level
import net.bestia.zone.ecs.ZoneServer
import net.bestia.zone.message.SelfSMSG
import org.springframework.stereotype.Component

@Component
class BestiaInfoFactory(
  private val playerBestiaRepository: PlayerBestiaRepository,
  private val zoneServer: ZoneServer
) {

  fun getBestiaInfo(playerEntities: Collection<ConnectionInfoService.PlayerEntity>): List<SelfSMSG.BestiaInfo> {
    val playerBestiasById = playerBestiaRepository.findAllById(playerEntities.map { it.playerBestiaId })
      .associateBy { it.id }

    // Get available bestias by combining entity info with player bestia data
    return playerEntities.map { (playerBestiaId, entityId) ->
      val playerBestia = playerBestiasById[playerBestiaId]
        ?: throw PlayerBestiaNotFoundException(playerBestiaId)

      zoneServer.withEntityReadLockOrThrow(entityId) { entity ->
        val position = entity.getOrThrow(Position::class).toVec3L()
        val level = entity.getOrThrow(Level::class).level

        SelfSMSG.BestiaInfo(
          entityId = entityId,
          mobId = playerBestia.bestia.id.toInt(),
          name = playerBestia.name,
          level = level,
          position = position
        )
      }
    }
  }
}