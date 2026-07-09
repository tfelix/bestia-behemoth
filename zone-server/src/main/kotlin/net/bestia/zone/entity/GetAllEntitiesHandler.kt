package net.bestia.zone.entity

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.EntityAOIService
import net.bestia.zone.ecs.item.ItemVisual
import net.bestia.zone.ecs.movement.Path
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.movement.Speed
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.ecs.bestia.BestiaVisual
import net.bestia.zone.ecs.bestia.BestiaVisualComponentSMSG
import net.bestia.zone.ecs.item.ItemVisualComponentSMSG
import net.bestia.zone.ecs.account.MasterVisual
import net.bestia.zone.util.EntityId
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.ecs.account.MasterVisualComponentSMSG
import net.bestia.zone.message.SMSG
import net.bestia.zone.ecs.movement.PathSMSG
import net.bestia.zone.ecs.movement.PositionSMSG
import net.bestia.zone.ecs.movement.SpeedSMSG
import net.bestia.zone.message.InMessageProcessor
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.util.AccountId
import org.springframework.stereotype.Component
import kotlin.reflect.KClass

/**
 * Handles the client message that sends out all entities.
 */
@Component
class GetAllEntitiesHandler(
  private val outMessageProcessor: OutMessageProcessor,
  private val connectionInfoService: ConnectionInfoService,
  private val aoiService: EntityAOIService,
  private val world: WorldView,
) : InMessageProcessor.IncomingMessageHandler<GetAllEntitiesCMSG> {
  override val handles: KClass<GetAllEntitiesCMSG> = GetAllEntitiesCMSG::class

  override fun handle(msg: GetAllEntitiesCMSG): Boolean {
    LOG.trace { "RX: $msg" }

    val queryPos = findPositionOfActive(msg.playerId)
    val entitiesInRange = getEntitiesInRange(queryPos)

    // Extract the component information to send out as an update. All component reads happen inside
    // a single lock-held read scope so nothing is touched off the tick thread.
    val outMessages = world.read {
      entitiesInRange.flatMap { eid ->
        listOfNotNull(
          tryBuildBestiaComponent(eid),
          tryBuildPositionComponent(eid),
          tryBuildMasterComponent(eid),
          tryBuildPathComponent(eid),
          tryBuildSpeedComponent(eid),
          tryBuildLootComponent(eid)
        )
      }
    }

    outMessageProcessor.sendToPlayer(msg.playerId, outMessages)

    return true
  }

  private fun World.tryBuildMasterComponent(entityId: EntityId): SMSG? {
    val masterComp = get(entityId, MasterVisual::class) ?: return null

    return MasterVisualComponentSMSG(
      entityId = entityId,
      skinColor = masterComp.skinColor,
      hairColor = masterComp.hairColor,
      face = masterComp.face,
      body = masterComp.body,
      hair = masterComp.hair
    )
  }

  private fun World.tryBuildBestiaComponent(entityId: EntityId): SMSG? {
    val bestiaComp = get(entityId, BestiaVisual::class) ?: return null

    return BestiaVisualComponentSMSG(entityId, bestiaComp.id)
  }

  private fun World.tryBuildPositionComponent(entityId: EntityId): SMSG? {
    val positionComp = get(entityId, Position::class) ?: return null

    return PositionSMSG(entityId, positionComp.toVec3L())
  }

  private fun World.tryBuildPathComponent(entityId: EntityId): SMSG? {
    val pathComp = get(entityId, Path::class) ?: return null

    return PathSMSG(entityId, pathComp.path)
  }

  private fun World.tryBuildSpeedComponent(entityId: EntityId): SMSG? {
    val speedComp = get(entityId, Speed::class) ?: return null

    return SpeedSMSG(entityId, speedComp.speed)
  }

  private fun World.tryBuildLootComponent(entityId: EntityId): SMSG? {
    val itemVisualComp = get(entityId, ItemVisual::class) ?: return null

    return ItemVisualComponentSMSG(entityId, itemVisualComp.itemId.toInt(), itemVisualComp.amount, itemVisualComp.uniqueId)
  }

  private fun findPositionOfActive(accountId: AccountId): Vec3L {
    val activeEntity = connectionInfoService.getActiveEntityId(accountId)

    return world.read { getOrThrow(activeEntity, Position::class).toVec3L() }
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
