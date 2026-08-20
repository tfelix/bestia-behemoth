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
import net.bestia.zone.navigation.local.LocalWalkQuery
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
  private val walkQuery: LocalWalkQuery,
) : InMessageProcessor.IncomingMessageHandler<MoveActiveEntityCMSG> {
  override val handles = MoveActiveEntityCMSG::class

  override fun handle(msg: MoveActiveEntityCMSG): Boolean {
    LOG.trace { "RX: $msg" }

    val activeEntityId = connectionInfoService.getActiveEntityId(msg.playerId)

    // Any movement command (including an empty-path "stop", which the client's logout Cancel button
    // sends) counts as player activity and aborts a pending logout.
    logoutCancelService.cancelLogout(activeEntityId)

    // Casting and crafting are both stationary: any movement command interrupts either. Deliberately done
    // before the path is validated - the player expressed intent to move, so the channel dies even if the
    // path is rejected below. The client blocks movement clicks while casting, so this is the authoritative
    // backstop.
    castCancelService.cancelCast(activeEntityId)
    castCancelService.cancelCraft(activeEntityId)

    world.modify(activeEntityId) { id ->
      if (msg.path.isEmpty()) {
        // An empty path is a stop request: drop any current path.
        remove(id, Path::class)
        return@modify
      }

      val position = get(id, Position::class)

      // Without a known position there is nothing to validate a step against, so the path is trusted as it
      // used to be unconditionally. With one, the path is walked and cut at the first step that is not
      // horizontally adjacent or crosses too steep a rise - see walkableStepsOf.
      val validPath = if (position == null) msg.path else walkableStepsOf(position.toVec3L(), msg.path)

      if (validPath.isEmpty()) {
        LOG.warn {
          "Dropping move for entity $id: path start ${msg.path.first()} is not reachable from current " +
            "position (${position?.x}, ${position?.y}, ${position?.z})"
        }
        return@modify
      }

      val existing = get(id, Path::class)
      if (existing != null) {
        existing.setPath(validPath)
      } else {
        add(id, Path(validPath.toMutableList()))
      }
    }

    return true
  }

  /**
   * The longest prefix of [path] reachable one step at a time from [start], stopping - not refusing the whole
   * path - at the first step that is not horizontally adjacent or that [LocalWalkQuery] positively refuses.
   *
   * Truncation rather than rejection matches how a click-to-move path is drawn client-side:
   * `path_calculator.gd` says outright that it ignores terrain, so a click across a slope or the lip of a
   * carved hole is routine, not hostile input. Stopping the walk at the edge is what an ordinary click into a
   * wall already looks like; a denial toast for every such click would be noise for something the player
   * caused by looking at the wrong spot on screen, not by doing anything wrong.
   *
   * [LocalWalkQuery.canStep] only answers for a chunk whose derived walkability tile already exists, and
   * nothing in this build populates that tile ahead of the first query for it - the same reason a fresh
   * spawn's own chunk is `isResident == false` moments after the manifest that just streamed it. Refusing a
   * step on that alone would strand a player in the chunk they just arrived in, which is worse than the
   * validation this is meant to add. So a side this build cannot yet vouch for is treated as walkable rather
   * than blocked - the same trade [net.bestia.zone.navigation.graph.MacroGraphService.isStillPassable] makes
   * for a structural edge whose chunk is not resident: absence of evidence is not evidence of a wall.
   */
  private fun walkableStepsOf(start: Vec3L, path: List<Vec3L>): List<Vec3L> {
    val walkable = ArrayList<Vec3L>(path.size)
    var from = start

    for (step in path) {
      if (!isAdjacent(from, step)) break
      if (walkQuery.isResident(from) && walkQuery.isResident(step) && !walkQuery.canStep(from, step)) break
      walkable.add(step)
      from = step
    }

    return walkable
  }

  companion object {
    private val LOG = KotlinLogging.logger { }

    private fun isAdjacent(from: Vec3L, target: Vec3L): Boolean {
      return abs(from.x - target.x) <= 1 && abs(from.y - target.y) <= 1
    }
  }
}
