package net.bestia.zone.landmark.script

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.chat.ChatSMSG
import net.bestia.zone.chat.ChatType
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.ecs.Landmark
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.landmark.LandmarkScript
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** The plainest landmark: a click tells the player what they are looking at. */
@Component
class ExamineLandmarkScript(
  private val outMessageProcessor: OutMessageProcessor
) : LandmarkScript {

  override fun onInteract(world: World, actorId: EntityId, landmarkId: EntityId): OpError? {
    val landmark = world.get(landmarkId, Landmark::class) ?: return null
    val landmarkAt = world.get(landmarkId, Position::class)?.toVec3L() ?: return null
    val actorAt = world.get(actorId, Position::class)?.toVec3L() ?: return null
    val accountId = world.get(actorId, Account::class)?.accountId ?: return null

    // The client walks into reach before it sends the click, so a click from further off is stale.
    if (actorAt.distance(landmarkAt) > EXAMINE_RANGE) return null

    val text = "You see a ${landmark.kind.label}."
    outMessageProcessor.sendToPlayer(accountId, ChatSMSG(type = ChatType.COMMAND, text = text))
    return null
  }

  private companion object {
    const val EXAMINE_RANGE = 8L
  }
}
