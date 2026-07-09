package net.bestia.zone.account.master

import net.bestia.zone.bestia.PlayerBestiaNotFoundException
import net.bestia.zone.bestia.PlayerBestiaRepository
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.ecs.battle.status.Level
import net.bestia.zone.ecs.core.World
import net.bestia.zone.message.SelfSMSG
import org.springframework.stereotype.Component

@Component
class BestiaInfoFactory(
  private val playerBestiaRepository: PlayerBestiaRepository,
  private val world: World
) {

  fun getBestiaInfo(playerEntities: Collection<ConnectionInfoService.PlayerEntity>): List<SelfSMSG.BestiaInfo> {
    val playerBestiasById = playerBestiaRepository.findAllById(playerEntities.map { it.playerBestiaId })
      .associateBy { it.id }

    // Get available bestias by combining entity info with player bestia data
    return playerEntities.map { (playerBestiaId, entityId) ->
      val playerBestia = playerBestiasById[playerBestiaId]
        ?: throw PlayerBestiaNotFoundException(playerBestiaId)

      world.modifyOrThrow(entityId) { id ->
        val position = world.getOrThrow(id, Position::class).toVec3L()
        val level = world.getOrThrow(id, Level::class).level

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
