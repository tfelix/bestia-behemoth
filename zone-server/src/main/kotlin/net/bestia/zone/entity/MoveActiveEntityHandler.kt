package net.bestia.zone.entity

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.movement.Path
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.battle.skill.CastCancelService
import net.bestia.zone.ecs.logout.LogoutCancelService
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.InMessageProcessor
import org.springframework.stereotype.Component
import kotlin.math.abs

/**
 * Applies a movement request from a client by attaching a [Path] to the player's currently active
 * entity. The [net.bestia.zone.ecs.movement.MoveSystem] then advances the entity along the path and
 * the resulting position changes are synced back to nearby clients by the engine.
 */
@Component
class MoveActiveEntityHandler(
  private val connectionInfoService: ConnectionInfoService,
  private val world: WorldView,
  private val logoutCancelService: LogoutCancelService,
  private val castCancelService: CastCancelService,
) : InMessageProcessor.IncomingMessageHandler<MoveActiveEntityCMSG> {
  override val handles = MoveActiveEntityCMSG::class

  override fun handle(msg: MoveActiveEntityCMSG): Boolean {
    LOG.trace { "RX: $msg" }

    val activeEntityId = connectionInfoService.getActiveEntityId(msg.playerId)

    // Any movement command (including an empty-path "stop", which the client's logout Cancel button
    // sends) counts as player activity and aborts a pending logout.
    logoutCancelService.cancelLogout(activeEntityId)

    // Casting is stationary: any movement command interrupts it. Deliberately done before the path is
    // validated - the player expressed intent to move, so the cast dies even if the path is rejected
    // below. The client blocks movement clicks while casting, so this is the authoritative backstop.
    castCancelService.cancelCast(activeEntityId)

    world.modify(activeEntityId) { id ->
      if (msg.path.isEmpty()) {
        // An empty path is a stop request: drop any current path.
        remove(id, Path::class)
        return@modify
      }

      val position = get(id, Position::class)
      val firstStep = msg.path.first()
      if (position != null && !isAdjacent(position, firstStep)) {
        LOG.warn {
          "Dropping move for entity $id: path start $firstStep is not adjacent to current position " +
            "(${position.x}, ${position.y}, ${position.z})"
        }
        return@modify
      }

      val existing = get(id, Path::class)
      if (existing != null) {
        existing.setPath(msg.path)
      } else {
        add(id, Path(msg.path.toMutableList()))
      }
    }

    return true
  }

  companion object {
    private val LOG = KotlinLogging.logger { }

    private fun isAdjacent(position: Position, target: Vec3L): Boolean {
      return abs(position.x - target.x) <= 1 && abs(position.y - target.y) <= 1
    }
  }
}
