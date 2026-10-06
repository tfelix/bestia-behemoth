package net.bestia.zone.master

import net.bestia.zone.account.PlayerBestiaNotFoundException
import net.bestia.zone.account.persistence.PlayerBestiaRepository
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.battle.ecs.level.Level
import net.bestia.zone.ecs.core.EntityNotAliveException
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.message.SelfSMSG
import org.springframework.stereotype.Component

@Component
class BestiaInfoFactory(
  private val playerBestiaRepository: PlayerBestiaRepository,
  private val world: WorldView
) {

  fun getBestiaInfo(playerEntities: Collection<ConnectionInfoService.PlayerEntity>): List<SelfSMSG.BestiaInfo> {
    val playerBestiasById = playerBestiaRepository.findAllById(playerEntities.map { it.playerBestiaId })
      .associateBy { it.id }

    // Get available bestias by combining entity info with player bestia data
    return playerEntities.map { (playerBestiaId, entityId) ->
      val playerBestia = playerBestiasById[playerBestiaId]
        ?: throw PlayerBestiaNotFoundException(playerBestiaId)

      world.modify(entityId) { id ->
        val position = getOrThrow(id, Position::class).toVec3L()
        val level = getOrThrow(id, Level::class).level

        SelfSMSG.BestiaInfo(
          entityId = entityId,
          mobId = playerBestia.bestia.id.toInt(),
          name = playerBestia.name,
          level = level,
          position = position
        )
      } ?: throw EntityNotAliveException(entityId)
    }
  }
}
