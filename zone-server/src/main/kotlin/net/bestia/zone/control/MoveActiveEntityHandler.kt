package net.bestia.zone.control

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.config.WorldRulesConfig
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.modify
import net.bestia.zone.movement.ecs.Path
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.battle.ecs.attack.AttackCancelService
import net.bestia.zone.entity.ecs.DeadActionGuard
import net.bestia.zone.battle.ecs.skill.CastCancelService
import net.bestia.zone.logout.ecs.LogoutCancelService
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.TickMessageHandler
import net.bestia.zone.message.decoder
import net.bestia.zone.navigation.local.LocalWalkQuery
import org.springframework.stereotype.Component
import kotlin.math.abs

/**
 * Applies a movement request from a client by attaching a [Path] to the player's currently active
 * entity. The [net.bestia.zone.movement.ecs.MoveSystem] then advances the entity along the path and
 * the resulting position changes are synced back to nearby clients by the engine.
 */
@Component
class MoveActiveEntityHandler(
  private val connectionInfoService: ConnectionInfoService,
  private val logoutCancelService: LogoutCancelService,
  private val castCancelService: CastCancelService,
  private val attackCancelService: AttackCancelService,
  private val deadActionGuard: DeadActionGuard,
  private val walkQuery: LocalWalkQuery,
  private val zoneConfig: WorldRulesConfig,
  private val rateLimit: MoveRequestRateLimit,
) : TickMessageHandler<MoveActiveEntityCMSG> {
  override val wire = decoder(MessageCase.MOVE_ACTIVE_ENTITY) { accountId, envelope ->
    MoveActiveEntityCMSG.fromBnet(accountId, envelope.moveActiveEntity)
  }

  override fun handle(world: World, msg: MoveActiveEntityCMSG): Boolean {
    val activeEntityId = connectionInfoService.getActiveEntityId(msg.playerId)

    // Before the cancel calls: a corpse expresses no intent, so it must not even abort its own logout.
    if (deadActionGuard.refuses(world, activeEntityId, "move")) {
      return true
    }

    // Any movement command (including an empty-path "stop", which the client's logout Cancel button
    // sends) counts as player activity and aborts a pending logout.
    logoutCancelService.cancelLogout(world, activeEntityId)

    // Casting and crafting are both stationary: any movement command interrupts either. Deliberately done
    // before the path is validated - the player expressed intent to move, so the channel dies even if the
    // path is rejected below. The client blocks movement clicks while casting, so this is the authoritative
    // backstop.
    castCancelService.cancelCast(world, activeEntityId)
    castCancelService.cancelCraft(world, activeEntityId)

    // Walking away is how a player calls off a fight - there is no other message for it.
    attackCancelService.cancelAttack(world, activeEntityId)
    // After the cancels, so a spammed click still counts as intent and cannot be used to keep a cast alive,
    // and before the expensive half - the world lock and the broadcast - which is the part worth bounding.
    if (!rateLimit.spend(msg.playerId)) {
      LOG.debug { "Dropping move for account ${msg.playerId}: over its request rate" }
      return true
    }

    world.modify(activeEntityId) { id ->
      if (msg.path.isEmpty()) {
        // An empty path is a stop request: drop any current path.
        remove(id, Path::class)
        return@modify
      }

      val position = get(id, Position::class)
      val existing = get(id, Path::class)

      // A leg sent while the last one is still walked, so the walk does not stop for a round trip at every leg.
      val walkToExtend = if (msg.append) existing?.takeUnless { it.isEmpty } else null
      if (walkToExtend != null) {
        extend(walkToExtend, msg.path)
        return@modify
      }

      // Without a known position there is nothing to validate a step against, so the path is trusted as it
      // used to be unconditionally. With one, the path is walked and cut at the first step that is not
      // horizontally adjacent or crosses too steep a rise - see walkableStepsOf.
      // Capped before anything is checked: every step costs a check under the world lock and goes to every viewer.
      val requested = msg.path.take(zoneConfig.maxMovePathSteps)
      val validPath = if (position == null) requested else walkableStepsOf(position.toVec3L(), requested)

      if (validPath.isEmpty()) {
        LOG.warn {
          "Dropping move for entity $id: path start ${requested.first()} is not reachable from current " +
            "position (${position?.x}, ${position?.y}, ${position?.z})"
        }

        // The client drew that path from where *it* believes the entity stands, so a refusal that says
        // nothing leaves it believing exactly that: the next click produces the same unreachable path from
        // the same wrong tile, and the player is stuck for good rather than for one click. A dropped move is
        // therefore always evidence of a disagreement about the position, and this is the side that is right
        // - so it publishes. One authoritative push, the client snaps, and its next click is drawn from a
        // tile this can accept.
        //
        // Not a denial message: there is nothing for the player to do differently, and see walkableStepsOf on
        // why a toast for an ordinary misjudged click is noise. The gap is the server's to close.
        position?.markDirty()

        return@modify
      }

      if (existing != null) {
        existing.setPath(validPath)
      } else {
        add(id, Path(validPath.toMutableList()))
      }
    }

    return true
  }

  /** Validated from the walk's last waypoint, the same way a new path is validated from the position. */
  private fun extend(walk: Path, leg: List<Vec3L>) {
    val tail = walk.lastWaypoint ?: return
    val validLeg = walkableStepsOf(tail, leg)

    if (validLeg.isEmpty()) {
      // The client sends a fresh leg once the entity stops, so nothing is lost by ignoring this one.
      LOG.debug { "Ignoring a leg that does not join the walk at $tail: ${leg.firstOrNull()}" }
      return
    }

    walk.appendPath(validLeg)
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
   * [LocalWalkQuery.canStep] only answers for a chunk whose derived walkability tile already exists. That used
   * to be *no chunk at all* - nothing populated the store, so this validation never once refused a step and
   * every path was accepted whole. `ChunkStreamSystem` now tracks a chunk's structures when it enters somebody's
   * subscription, so the window is the handful of ticks the rebuild budget needs after a chunk arrives rather
   * than the life of the process. It is still a window: a fresh spawn's own chunk can be `isResident == false`
   * moments after the manifest that streamed it.
   *
   * Refusing a step on that alone would strand a player in the chunk they just arrived in, which is worse than
   * the validation this is meant to add. So a side that cannot yet be vouched for is treated as walkable rather
   * than blocked - the same trade [net.bestia.zone.navigation.graph.MacroGraphService.isStillPassable] makes
   * for a structural edge whose chunk is not resident: absence of evidence is not evidence of a wall.
   */
  private fun walkableStepsOf(start: Vec3L, path: List<Vec3L>): List<Vec3L> {
    val walkable = ArrayList<Vec3L>(path.size)
    var from = start

    for (requested in path) {
      if (!isAdjacent(from, requested)) break
      // The client's z is ignored: it chose which slab residency was looked up in, so a made-up height made
      // every step "not loaded" and skipped the walkability check. The height follows on from the last step.
      val ground = walkQuery.surfaceAt(Vec3L(requested.x, requested.y, from.z)) ?: from.z
      val step = Vec3L(requested.x, requested.y, ground)
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
