package net.bestia.zone.entity

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.EntityAOIService
import net.bestia.zone.ecs.item.ItemVisual
import net.bestia.zone.ecs.movement.Path
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.movement.Speed
import net.bestia.zone.ecs.session.ConnectionInfoService
import net.bestia.zone.ecs.bestia.BestiaVisual
import net.bestia.zone.ecs.bestia.BestiaVisualComponentSMSG
import net.bestia.zone.ecs.item.ItemVisualComponentSMSG
import net.bestia.zone.ecs.player.MasterVisual
import net.bestia.zone.ecs.core.EntityId
import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.MasterVisualComponentSMSG
import net.bestia.zone.message.SMSG
import net.bestia.zone.ecs.movement.PathSMSG
import net.bestia.zone.ecs.movement.PositionSMSG
import net.bestia.zone.ecs.movement.SpeedSMSG
import net.bestia.zone.message.processor.InMessageProcessor
import net.bestia.zone.message.processor.OutMessageProcessor
import net.bestia.zone.util.AccountId
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
  private val world: World,
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
        tryBuildSpeedComponent(eid),
        tryBuildLootComponent(eid)
      )
    }

    outMessageProcessor.sendToPlayer(msg.playerId, outMessages)

    return true
  }

  private fun tryBuildMasterComponent(entityId: EntityId): SMSG? {
    val masterComp = world.get(entityId, MasterVisual::class) ?: return null

    return MasterVisualComponentSMSG(
      entityId = entityId,
      skinColor = masterComp.skinColor,
      hairColor = masterComp.hairColor,
      face = masterComp.face,
      body = masterComp.body,
      hair = masterComp.hair
    )
  }

  private fun tryBuildBestiaComponent(entityId: EntityId): SMSG? {
    val bestiaComp = world.get(entityId, BestiaVisual::class) ?: return null

    return BestiaVisualComponentSMSG(entityId, bestiaComp.id)
  }

  private fun tryBuildPositionComponent(entityId: EntityId): SMSG? {
    val positionComp = world.get(entityId, Position::class) ?: return null

    return PositionSMSG(entityId, positionComp.toVec3L())
  }

  private fun tryBuildPathComponent(entityId: EntityId): SMSG? {
    val pathComp = world.get(entityId, Path::class) ?: return null

    return PathSMSG(entityId, pathComp.path)
  }

  private fun tryBuildSpeedComponent(entityId: EntityId): SMSG? {
    val speedComp = world.get(entityId, Speed::class) ?: return null

    return SpeedSMSG(entityId, speedComp.speed)
  }

  private fun tryBuildLootComponent(entityId: EntityId): SMSG? {
    val itemVisualComp = world.get(entityId, ItemVisual::class) ?: return null

    return ItemVisualComponentSMSG(entityId, itemVisualComp.itemId.toInt(), itemVisualComp.amount, itemVisualComp.uniqueId)
  }

  private fun findPositionOfActive(accountId: AccountId): Vec3L {
    val activeEntity = connectionInfoService.getActiveEntityId(accountId)

    return world.getOrThrow(activeEntity, Position::class).toVec3L()
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
