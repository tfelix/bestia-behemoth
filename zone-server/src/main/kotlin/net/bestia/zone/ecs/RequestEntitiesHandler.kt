package net.bestia.zone.ecs

import com.github.quillraven.fleks.Entity
import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.message.GetAllEntitiesCMSG
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.movement.Path
import net.bestia.zone.ecs.movement.Speed
import net.bestia.zone.ecs.session.ActiveEntityResolver
import net.bestia.zone.ecs.visual.BestiaVisual
import net.bestia.zone.ecs.visual.BestiaVisualComponentSMSG
import net.bestia.zone.ecs.visual.MasterVisual
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.MasterVisualComponentSMSG
import net.bestia.zone.message.SMSG
import net.bestia.zone.message.entity.PositionSMSG
import net.bestia.zone.message.entity.PathSMSG
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
  private val activeEntityResolver: ActiveEntityResolver,
  private val entityRegistry: EntityRegistry,
  private val aoiService: EntityAOIService,
  private val zoneServer: ZoneServer,
) : InMessageProcessor.IncomingMessageHandler<GetAllEntitiesCMSG> {
  override val handles: KClass<GetAllEntitiesCMSG> = GetAllEntitiesCMSG::class

  override fun handle(msg: GetAllEntitiesCMSG): Boolean {
    LOG.trace { "RX: $msg" }

    val queryPos = findPositionOfActive(msg.playerId)
    val entitiesInRange = getEntitiesInRange(queryPos)

    // Extract the component information to send out as an update.
    val outMessages = entitiesInRange.flatMap { (eid, e) ->
      listOfNotNull(
        tryBuildBestiaComponent(eid, e),
        tryBuildPositionComponent(eid, e),
        tryBuildMasterComponent(eid, e),
        tryBuildPathComponent(eid, e),
        tryBuildSpeedComponent(eid, e)
      )
    }

    outMessageProcessor.sendToPlayer(msg.playerId, outMessages)

    return true
  }

  private fun tryBuildMasterComponent(
    entityId: EntityId,
    entity: Entity
  ): SMSG? {
    return withComponentNotFoundCatch {
      val masterReader = MasterVisual.MasterVisualAcessor(entity)
      zoneServer.accessWorld(masterReader)

      MasterVisualComponentSMSG(
        entityId = entityId,
        skinColor = masterReader.skinColor,
        hairColor = masterReader.hairColor,
        face = masterReader.face,
        body = masterReader.body,
        hair = masterReader.hair
      )
    }
  }

  private fun tryBuildBestiaComponent(
    entityId: EntityId,
    entity: Entity
  ): SMSG? {
    return withComponentNotFoundCatch {
      val bestiaReader = BestiaVisual.BestiaVisualAcessor(entity)
      zoneServer.accessWorld(bestiaReader)

      BestiaVisualComponentSMSG(entityId, bestiaReader.id)
    }
  }

  private fun tryBuildPositionComponent(
    entityId: EntityId,
    entity: Entity
  ): SMSG? {
    return withComponentNotFoundCatch {
      val posReader = Position.PositionAcessor(entity)
      zoneServer.accessWorld(posReader)

      PositionSMSG(entityId, posReader.position)
    }
  }

  private fun tryBuildPathComponent(
    entityId: EntityId,
    entity: Entity
  ): SMSG? {
    return withComponentNotFoundCatch {
      val pathReader = Path.PathAcessor(entity)
      zoneServer.accessWorld(pathReader)

      PathSMSG(entityId, pathReader.path)
    }
  }

  private fun tryBuildSpeedComponent(
    entityId: EntityId,
    entity: Entity
  ): SMSG? {
    return withComponentNotFoundCatch {
      val speedReader = Speed.SpeedAcessor(entity)
      zoneServer.accessWorld(speedReader)

      SpeedSMSG(entityId, speedReader.speed)
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
    val activeEntity = activeEntityResolver.findActiveEntityByAccountIdOrThrow(accountId)

    val activeEntityPosReader = Position.PositionAcessor(activeEntity)
    zoneServer.accessWorld(activeEntityPosReader)

    return activeEntityPosReader.position
  }

  private fun getEntitiesInRange(queryPos: Vec3L): List<Pair<EntityId, Entity>> {
    val entityIdsInRange = aoiService.queryEntitiesInCube(queryPos, ENTITY_QUERY_RANGE)
    LOG.debug { "Entities in query range: $entityIdsInRange" }

    val entitiesInRange = entityIdsInRange
      .mapNotNull { eir ->
        entityRegistry.getEntity(eir)?.let { Pair(eir, it) }
      }

    if (entitiesInRange.size != entityIdsInRange.size) {
      LOG.warn { "Resolved entities $entitiesInRange do not match range queried IDs: $entitiesInRange!" }
    }

    return entitiesInRange
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
    private const val ENTITY_QUERY_RANGE = 30L
  }
}