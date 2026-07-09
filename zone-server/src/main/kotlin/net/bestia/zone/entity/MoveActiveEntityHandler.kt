package net.bestia.zone.entity

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.movement.Path
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.ecs.core.World
import net.bestia.zone.message.InMessageProcessor
import org.springframework.stereotype.Component

/**
 * Applies a movement request from a client by attaching a [Path] to the player's currently active
 * entity. The [net.bestia.zone.ecs.movement.MoveSystem] then advances the entity along the path and
 * the resulting position changes are synced back to nearby clients by the engine.
 */
@Component
class MoveActiveEntityHandler(
  private val connectionInfoService: ConnectionInfoService,
  private val world: World,
) : InMessageProcessor.IncomingMessageHandler<MoveActiveEntityCMSG> {
  override val handles = MoveActiveEntityCMSG::class

  override fun handle(msg: MoveActiveEntityCMSG): Boolean {
    LOG.trace { "RX: $msg" }

    val activeEntityId = connectionInfoService.getActiveEntityId(msg.playerId)

    world.modify(activeEntityId) { id ->
      if (msg.path.isEmpty()) {
        // An empty path is a stop request: drop any current path.
        world.remove(id, Path::class)
      } else {
        val existing = world.get(id, Path::class)
        if (existing != null) {
          existing.setPath(msg.path)
          world.markChanged(id, Path::class)
        } else {
          world.add(id, Path(msg.path.toMutableList()))
        }
      }
    }

    return true
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
