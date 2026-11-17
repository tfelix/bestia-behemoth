package net.bestia.zone.blackboard

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.place.ecs.Place
import net.bestia.zone.prop.PlayerStructureService
import net.bestia.zone.prop.PropInteraction
import net.bestia.zone.script.ScriptArgs
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** Clicking a blackboard opens its window. Every board shows the same list, so the board only says where it is. */
@Component
class BlackboardInteraction(
  private val outMessageProcessor: OutMessageProcessor,
) : PropInteraction {

  override val kind = StaticEntityKind.BLACKBOARD

  override fun interact(world: World, actorId: EntityId, propId: EntityId): OpError? {
    return interact(world, actorId, propId, ScriptArgs.EMPTY)
  }

  override fun interact(world: World, actorId: EntityId, propId: EntityId, args: ScriptArgs): OpError? {
    if (!isInReach(world, actorId, propId)) {
      return OpError.TALK_OUT_OF_RANGE
    }
    val accountId = world.get(actorId, Account::class)?.accountId ?: return null
    val placeName = world.get(actorId, Place::class)?.place?.name ?: ""

    outMessageProcessor.sendToPlayer(accountId, BlackboardSMSG(propId, placeName, quests = emptyList()))

    return null
  }

  private fun isInReach(world: World, actorId: EntityId, boardId: EntityId): Boolean {
    val actorAt = world.get(actorId, Position::class)?.toVec3L() ?: return false
    val boardAt = world.get(boardId, Position::class)?.toVec3L() ?: return false

    return actorAt.distance(boardAt) <= PlayerStructureService.RANGE_TILES
  }
}
