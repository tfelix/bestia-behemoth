package net.bestia.zone.travel

import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.movement.ecs.Path
import net.bestia.zone.navigation.NavigationConfig
import net.bestia.zone.navigation.NavigationService
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Turns the next stretch towards an aim into steps over the ground as it is now.
 *
 * Searched again on [TravelConfig.repathInterval] even while a leg is walked, which is what lets a traveller
 * walk round a wall built in front of it. The new leg joins the walk on the tile being stepped onto, so the
 * step under way is never lost.
 */
@Component
class LegRefiner(
  private val navigation: NavigationService,
  private val budget: TravelBudget,
  private val config: TravelConfig,
  private val navigationConfig: NavigationConfig,
) {

  /**
   * A leg that ends on the aim is left alone: [net.bestia.zone.movement.ecs.MoveSystem] still refuses a step
   * somebody built a wall on, and the walk then runs out and is refined. Searching it again on the interval
   * would only replace a client's own first leg with an equally good one it never asked for.
   */
  fun isDue(journey: Journey, path: Path?, aim: Vec3L): Boolean {
    if (path == null || path.isEmpty) return true

    val endsOnAim = path.lastWaypoint?.let { it.x == aim.x && it.y == aim.y } == true
    if (endsOnAim) return false

    return path.remainingSteps <= LOOKAHEAD_STEPS ||
      journey.secondsSinceRefined >= config.repathInterval.toMillis() / 1000f
  }

  /** Does nothing when this tick's searches are spent: the traveller keeps its leg and asks again next tick. */
  fun refineNextLeg(world: World, entity: EntityId, journey: Journey, position: Vec3L, aim: Vec3L) {
    if (!budget.trySpend()) return

    val path = world.get(entity, Path::class)?.takeUnless { it.isEmpty }
    val from = path?.next ?: position
    val leg = legFrom(from, aim)

    journey.secondsSinceRefined = 0f
    if (leg.isEmpty()) return

    if (path == null) {
      world.add(entity, Path(leg.toMutableList()))
    } else if (leg != path.path.drop(1)) {
      // Only a different way is announced; the same way found again is not news to anyone watching.
      path.rerouteAfter(0, leg)
    }
  }

  private fun legFrom(from: Vec3L, aim: Vec3L): List<Vec3L> {
    val towards = withinSearchSpan(from, aim)
    return navigation.pathOutsideBudget(from, towards)
      ?: navigation.stepToward(from, towards)?.let { listOf(it) }
      ?: emptyList()
  }

  /**
   * [aim] itself when a search can reach it, otherwise as far towards it in a straight line as one can. At the
   * traveller's own height: a map click and a macro node do not know the ground's, and the search finds it.
   */
  private fun withinSearchSpan(from: Vec3L, aim: Vec3L): Vec3L {
    val dx = aim.x - from.x
    val dy = aim.y - from.y
    val distance = maxOf(Math.abs(dx), Math.abs(dy))
    val span = navigationConfig.localSearchSpan
    if (distance <= span) return Vec3L(aim.x, aim.y, from.z)

    val stride = span.toDouble() / distance
    return Vec3L(from.x + Math.round(dx * stride), from.y + Math.round(dy * stride), from.z)
  }

  private companion object {
    /** Refined this many steps before the leg runs out, so the walk never pauses between legs. */
    const val LOOKAHEAD_STEPS = 6
  }
}
