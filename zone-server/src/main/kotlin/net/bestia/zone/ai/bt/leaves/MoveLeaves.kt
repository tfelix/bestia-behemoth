package net.bestia.zone.ai.bt.leaves

import net.bestia.zone.ai.bt.Locomotion
import net.bestia.zone.ai.core.behavior.BtContext
import net.bestia.zone.ai.core.behavior.BtNode
import net.bestia.zone.ai.core.behavior.Status
import net.bestia.zone.ecs.movement.Path
import net.bestia.zone.geometry.Vec3L
import kotlin.random.Random

/**
 * Walks to a *concrete* [target], succeeding once within [arrivalRadius] tiles of it.
 *
 * This is the shape the old `MoveToTargetLeaf` could not have: it took no parameters, so it had to
 * read the destination back out of the shared brain component, which meant one leaf class per kind of
 * destination and no way to plan two different walks in one plan. Because the planner now grounds
 * actions against concrete values, the template hands the destination straight to the leaf — so the
 * same `MoveTo` serves "walk to that vegetation spot", "walk back home" and "close on that target".
 *
 * FAILURE, not endless RUNNING, when there is no route: a target across a river is genuinely
 * unreachable, and reporting RUNNING would keep a doomed plan alive while the creature stood still.
 * FAILURE lets the think stage pick something else.
 */
class MoveTo(
  private val target: Vec3L,
  private val locomotion: Locomotion,
  private val arrivalRadius: Long = 0,
) : BtNode {

  override fun tick(context: BtContext): Status {
    if (locomotion.distanceTo(context.world, context.entityId, target) <= arrivalRadius) {
      return Status.SUCCESS
    }

    return if (locomotion.stepToward(context, target)) Status.RUNNING else Status.FAILURE
  }

  override fun toString(): String = "MoveTo($target, arrive<=$arrivalRadius)"
}

/**
 * Ambles about within [radius] of [home]: one navigated leg of a few tiles, then [pauseSeconds] standing
 * still, repeated.
 *
 * The pause is what makes roaming cheap rather than a nicety of how it looks. Each leg costs every observer
 * two broadcasts - the `Path` and the stop that its removal is - so a creature that set off again the moment
 * it arrived would broadcast continuously, and a whole zone of them would drain the tick's pathfinding
 * budget between them.
 *
 * Always RUNNING, even when penned in by terrain: wandering is what a creature does while nothing better
 * applies, and reporting FAILURE would make the think stage replan on every single tick for as long as it
 * stayed hemmed in. The action that owns this tree is the one that decides when wandering is *done* — see
 * the restlessness key in the bestia domain — so this leaf never has to.
 */
class Wander(
  private val home: Vec3L,
  private val locomotion: Locomotion,
  private val radius: Long,
  private val pauseSeconds: ClosedFloatingPointRange<Float> = DEFAULT_PAUSE_SECONDS,
  private val random: Random = Random.Default,
) : BtNode {

  init {
    require(pauseSeconds.start > 0f) { "Wander requires a positive pause, got $pauseSeconds" }
  }

  private var walkingLeg = false
  private var pauseRemaining = 0f

  override fun tick(context: BtContext): Status {
    if (walkingLeg) {
      if (locomotion.isMoving(context.world, context.entityId)) return Status.RUNNING

      // Arrived, or something else took the path away. Either way this leg is over.
      walkingLeg = false
      pauseRemaining = drawPause()

      return Status.RUNNING
    }

    if (pauseRemaining > 0f) {
      pauseRemaining -= context.deltaTime

      return Status.RUNNING
    }

    // A leg nothing could path to is paused over as though it had been walked, rather than asked for again
    // on the next tick: the ground has not changed since, and the search is not free.
    walkingLeg = locomotion.wanderLeg(context, home, radius)
    if (!walkingLeg) pauseRemaining = drawPause()

    return Status.RUNNING
  }

  private fun drawPause(): Float {
    return pauseSeconds.start + random.nextFloat() * (pauseSeconds.endInclusive - pauseSeconds.start)
  }

  override fun toString(): String = "Wander(around $home, r=$radius)"

  companion object {
    /** How long a creature stands about between legs. */
    val DEFAULT_PAUSE_SECONDS = 3f..5f
  }
}

/**
 * Stays where it is, and drops any waypoints already handed to the movement system.
 *
 * The drop is the whole leaf. A plan step's tree replaces the previous one immediately, but a `Path` the
 * movement system is already walking outlives it - so an agent that arrived somewhere and then decided to
 * stand there would wander off down the tail of the journey that brought it. `Sleep` carries the same
 * guard for the same reason.
 */
object StandStill : BtNode {

  override fun tick(context: BtContext): Status {
    context.world.remove(context.entityId, Path::class)
    return Status.RUNNING
  }

  override fun toString(): String = "StandStill"
}
