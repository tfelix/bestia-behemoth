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

  fun isDue(journey: Journey, path: Path?): Boolean {
    if (path == null || path.remainingSteps <= LOOKAHEAD_STEPS) return true
    return journey.secondsSinceRefined >= config.repathInterval.toMillis() / 1000f
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
    } else {
      path.rerouteAfter(0, leg)
    }
  }

  private fun legFrom(from: Vec3L, aim: Vec3L): List<Vec3L> {
    val towards = withinSearchSpan(from, aim)
    return navigation.pathOutsideBudget(from, towards)
      ?: navigation.stepToward(from, towards)?.let { listOf(it) }
      ?: emptyList()
  }

  /** [aim] itself when a search can reach it, otherwise as far towards it in a straight line as one can. */
  private fun withinSearchSpan(from: Vec3L, aim: Vec3L): Vec3L {
    val dx = aim.x - from.x
    val dy = aim.y - from.y
    val distance = maxOf(Math.abs(dx), Math.abs(dy))
    val span = navigationConfig.localSearchSpan
    if (distance <= span) return aim

    val stride = span.toDouble() / distance
    return Vec3L(from.x + Math.round(dx * stride), from.y + Math.round(dy * stride), from.z)
  }

  private companion object {
    /** Refined this many steps before the leg runs out, so the walk never pauses between legs. */
    const val LOOKAHEAD_STEPS = 6
  }
}
