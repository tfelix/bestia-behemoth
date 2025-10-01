package net.bestia.zone.entity

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.ComponentNotFoundException
import net.bestia.zone.ecs.EntityAOIService
import net.bestia.zone.ecs.movement.Path
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.movement.Speed
import net.bestia.zone.ecs.session.ConnectionInfoService
import net.bestia.zone.ecs.visual.BestiaVisual
import net.bestia.zone.ecs.visual.BestiaVisualComponentSMSG
import net.bestia.zone.ecs.visual.MasterVisual
import net.bestia.zone.ecs2.ZoneServer
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.MasterVisualComponentSMSG
import net.bestia.zone.message.SMSG
import net.bestia.zone.message.entity.PathSMSG
import net.bestia.zone.message.entity.PositionSMSG
import net.bestia.zone.message.entity.SpeedSMSG
import net.bestia.zone.message.processor.InMessageProcessor
import net.bestia.zone.message.processor.OutMessageProcessor
import net.bestia.zone.util.AccountId
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component
import kotlin.reflect.KClass

/**
 * Handles the client message that sends out all entities.
 */
@Component
class RequestEntitiesHandler(
  private val outMessageProcessor: OutMessageProcessor,
  private val connectionInfoService: ConnectionInfoService,
  private val aoiService: EntityAOIService,
  private val zoneServer: ZoneServer,
) : InMessageProcessor.IncomingMessageHandler<GetAllEntitiesCMSG> {
  override val handles: KClass<GetAllEntitiesCMSG> = GetAllEntitiesCMSG::class

  override fun handle(msg: GetAllEntitiesCMSG): Boolean {
    LOG.trace { "RX: $msg" }

    val queryPos = findPositionOfActive(msg.playerId)
    val entitiesInRange = getEntitiesInRange(queryPos)

    // Extract the component information to send out as an update.
    val outMessages = entitiesInRange.flatMap { eid ->
      listOfNotNull(
        tryBuildBestiaComponent(eid),
        tryBuildPositionComponent(eid),
        tryBuildMasterComponent(eid),
        tryBuildPathComponent(eid),
        tryBuildSpeedComponent(eid)
      )
    }

    outMessageProcessor.sendToPlayer(msg.playerId, outMessages)

    return true
  }

  private fun tryBuildMasterComponent(
    entityId: EntityId,
  ): SMSG? {
    return withComponentNotFoundCatch {
      zoneServer.withEntityReadLockOrThrow(entityId) { entity ->
        val masterComp = entity.getOrThrow(MasterVisual::class)

        MasterVisualComponentSMSG(
          entityId = entityId,
          skinColor = masterComp.skinColor,
          hairColor = masterComp.hairColor,
          face = masterComp.face,
          body = masterComp.body,
          hair = masterComp.hair
        )
      }
    }
  }

  private fun tryBuildBestiaComponent(
    entityId: EntityId,
  ): SMSG? {
    return withComponentNotFoundCatch {
      zoneServer.withEntityReadLockOrThrow(entityId) { entity ->
        val bestiaComp = entity.getOrThrow(BestiaVisual::class)

        BestiaVisualComponentSMSG(entityId, bestiaComp.id)
      }
    }
  }

  private fun tryBuildPositionComponent(
    entityId: EntityId,
  ): SMSG? {
    return withComponentNotFoundCatch {
      zoneServer.withEntityReadLockOrThrow(entityId) { entity ->
        val positionComp = entity.getOrThrow(Position::class)

        PositionSMSG(entityId, positionComp.toVec3L())
      }
    }
  }

  private fun tryBuildPathComponent(
    entityId: EntityId,
  ): SMSG? {
    return withComponentNotFoundCatch {
      zoneServer.withEntityReadLockOrThrow(entityId) { entity ->
        val pathComp = entity.getOrThrow(Path::class)

        PathSMSG(entityId, pathComp.path)
      }
    }
  }

  private fun tryBuildSpeedComponent(
    entityId: EntityId,
  ): SMSG? {
    return withComponentNotFoundCatch {
      zoneServer.withEntityReadLockOrThrow(entityId) { entity ->
        val speedComp = entity.getOrThrow(Speed::class)

        SpeedSMSG(entityId, speedComp.speed)
      }
    }
  }

  private fun withComponentNotFoundCatch(fn: () -> SMSG): SMSG? {
    return try {
      fn()
    } catch (_: ComponentNotFoundException) {
      null
    }
  }

  private fun findPositionOfActive(accountId: AccountId): Vec3L {
    val activeEntity = connectionInfoService.getActiveEntityId(accountId)

    return zoneServer.withEntityReadLockOrThrow(activeEntity) { entity ->
      val positionComp = entity.getOrThrow(Position::class)
      positionComp.toVec3L()
    }
  }

  private fun getEntitiesInRange(queryPos: Vec3L): List<EntityId> {
    val entityIdsInRange = aoiService.queryEntitiesInCube(queryPos, ENTITY_QUERY_RANGE)
    LOG.debug { "Entities in query range: $entityIdsInRange" }

    return entityIdsInRange.toList()
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
    private const val ENTITY_QUERY_RANGE = 30L
  }
}