package net.bestia.zone.account.master

import net.bestia.zone.bestia.PlayerBestiaNotFoundException
import net.bestia.zone.bestia.PlayerBestiaRepository
import net.bestia.zone.ecs.EntityInconsistencyException
import net.bestia.zone.ecs.EntityRegistry
import net.bestia.zone.ecs.ZoneServer
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.session.ConnectionInfoService
import net.bestia.zone.ecs.status.Level
import net.bestia.zone.message.SelfSMSG
import org.springframework.stereotype.Component

@Component
class BestiaInfoFactory(
  private val playerBestiaRepository: PlayerBestiaRepository,
  private val entityRegistry: EntityRegistry,
  private val zoneServer: ZoneServer
) {

  fun getBestiaInfo(playerEntities: Collection<ConnectionInfoService.PlayerEntity>): List<SelfSMSG.BestiaInfo> {
    val playerBestiasById = playerBestiaRepository.findAllById(playerEntities.map { it.playerBestiaId })
      .associateBy { it.id }

    // Get available bestias by combining entity info with player bestia data
    return playerEntities.map { (playerBestiaId, entityId) ->
      val playerBestia = playerBestiasById[playerBestiaId]
        ?: throw PlayerBestiaNotFoundException(playerBestiaId)

      val entity = entityRegistry.getEntity(entityId)
        ?: throw EntityInconsistencyException("No entity $entityId was found in the registry for an listed active session entity")

      val positionReader = Position.PositionAcessor(entity)
      val levelReader = Level.LevelAcessor(entity)

      zoneServer.accessWorld(positionReader)
      zoneServer.accessWorld(levelReader)

      SelfSMSG.BestiaInfo(
        entityId = entityId,
        mobId = playerBestia.bestia.id.toInt(),
        name = playerBestia.name,
        level = levelReader.level,
        position = positionReader.position
      )
    }
  }
}