package net.bestia.zone.control

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.config.WorldRulesConfig
import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.movement.ecs.Path
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.navigation.local.LocalWalkQuery
import net.bestia.zone.travel.Journey
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component
import kotlin.math.abs

/**
 * Walks the steps a client already walks towards the first waypoint of a move, as far as each one is valid.
 *
 * The client predicts its own click so it moves at once, and keeps to that walk until the server answers.
 * Planning the leg here instead would answer with a different, equally good walk the client ignores - so the
 * client's steps are taken as they are when they are walkable, and the journey plans only what lies past
 * them.
 */
@Component
class ClientLeg(
  private val walkQuery: LocalWalkQuery,
  private val zoneConfig: WorldRulesConfig,
) {

  fun adopt(world: World, id: EntityId, steps: List<Vec3L>) {
    if (steps.isEmpty()) return

    val position = world.get(id, Position::class)
    val existing = world.get(id, Path::class)?.takeUnless { it.isEmpty }

    // Searched again only once these run out, not straight away: see LegRefiner.isDue.
    world.get(id, Journey::class)?.secondsSinceRefined = 0f

    // Capped before anything is checked: every step costs a check under the world lock and goes to every viewer.
    val requested = steps.take(zoneConfig.maxMovePathSteps)

    // A route picked mid-walk starts on the walk under way, at the tile the client is stepping onto. Measured
    // from the last tile reached instead - all the position says - a click straight ahead was two tiles away by
    // the middle of every step and was refused, which snapped the client back. Joined where it touches the
    // walk, neither side cuts the step under way, and a client a trip ahead of this one joins further along.
    val joinAt = existing?.indexOfColumn(requested.first()) ?: -1
    if (existing != null && joinAt >= 0) {
      existing.rerouteAfter(joinAt, walkableStepsOf(existing.path[joinAt], requested.drop(1)))
      return
    }

    // A client finishes a walk a trip before this does, and the next click is drawn from the tile it ended on: the
    // walk's last one, which this has not reached yet. Measured from where this stands, that click starts a tile
    // too far away and was dropped, which snapped the client back to a tile it had already left - and the walk then
    // carried on to where the client had been. So it is joined on after the walk instead.
    val last = existing?.lastWaypoint
    if (existing != null && last != null && isAdjacent(last, requested.first())) {
      val steps = walkableStepsOf(last, requested)
      if (steps.isNotEmpty()) {
        existing.appendPath(steps)
        return
      }
    }

    // Without a known position there is nothing to validate a step against, so the path is trusted as it
    // used to be unconditionally. With one, the path is walked and cut at the first step that is not
    // horizontally adjacent or crosses too steep a rise - see walkableStepsOf.
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

      return
    }

    if (existing != null) {
      existing.setPath(validPath)
    } else {
      world.add(id, Path(validPath.toMutableList()))
    }
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

  private companion object {
    private val LOG = KotlinLogging.logger { }

    private fun isAdjacent(from: Vec3L, target: Vec3L): Boolean {
      return abs(from.x - target.x) <= 1 && abs(from.y - target.y) <= 1
    }
  }
}
