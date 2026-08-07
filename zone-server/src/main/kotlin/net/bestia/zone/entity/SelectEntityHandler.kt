package net.bestia.zone.entity

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ai.ecs.PlayerControlled
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.ecs.core.session.EntityNotOwnedSessionException
import net.bestia.zone.message.InMessageProcessor
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Selects the entity the player wants to focus on. This means this entity will
 * be used as reference point for the player updates.
 */
@Component
class SelectEntityHandler(
  private val connectionInfoService: ConnectionInfoService,
  private val world: WorldView,
) : InMessageProcessor.IncomingMessageHandler<SelectEntityCMSG> {
  override val handles = SelectEntityCMSG::class

  override fun handle(msg: SelectEntityCMSG): Boolean {
    LOG.trace { "RX: $msg" }

    // Read before switching: this is the entity that is about to stop being driven and start looking after
    // itself again.
    val previous = runCatching { connectionInfoService.getActiveEntityId(msg.playerId) }.getOrNull()

    try {
      connectionInfoService.activateEntity(msg.playerId, msg.entityId)
    } catch (e: EntityNotOwnedSessionException) {
      LOG.warn { "Can not select entity, no entity ${msg.entityId} found for player ${msg.playerId}" }
      return false
    }

    moveControlMarker(from = previous, to = msg.entityId)

    return true
  }

  /**
   * Hands the [PlayerControlled] marker over, which is what stops the AI thinking for whichever creature the
   * player is actually driving while letting the one they just left resume its standing order.
   *
   * Done here rather than inside `ConnectionInfoService` because that service is a pure session map with no
   * access to the world, and giving it one would couple session bookkeeping to the ECS. Each side keeps its own
   * notion of "active" and this handler is the seam that already knows about both.
   */
  private fun moveControlMarker(from: EntityId?, to: EntityId) {
    if (from == to) return

    from?.let { previous ->
      world.modify(previous) { id -> remove(id, PlayerControlled::class) }
    }
    world.modify(to) { id -> add(id, PlayerControlled) }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
