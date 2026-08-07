package net.bestia.zone.ai.bt.leaves

import net.bestia.zone.ai.bt.Locomotion
import net.bestia.zone.ai.core.behavior.BtContext
import net.bestia.zone.ai.core.behavior.BtNode
import net.bestia.zone.ai.core.behavior.Status
import net.bestia.zone.geometry.Vec3L

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
 * Backs away from [threat] until at least [safeDistance] tiles from it.
 *
 * SUCCESS once far enough away, FAILURE when cornered with nowhere legal to back into — which is a
 * real outcome the think stage needs to hear about, because a cornered creature should be allowed to
 * pick a different goal (turn and fight) rather than shuffle against a wall forever.
 */
class FleeFrom(
  private val threat: Vec3L,
  private val locomotion: Locomotion,
  private val safeDistance: Long,
) : BtNode {

  override fun tick(context: BtContext): Status {
    if (locomotion.distanceTo(context.world, context.entityId, threat) >= safeDistance) {
      return Status.SUCCESS
    }

    return if (locomotion.stepAwayFrom(context, threat)) Status.RUNNING else Status.FAILURE
  }

  override fun toString(): String = "FleeFrom($threat, safe>=$safeDistance)"
}

/**
 * Ambles about within [radius] of [home]. Always RUNNING, even when penned in by terrain: wandering is
 * what a creature does while nothing better applies, and reporting FAILURE would make the think stage
 * replan on every single tick for as long as it stayed hemmed in.
 *
 * The action that owns this tree is the one that decides when wandering is *done* — see the
 * restlessness key in the bestia domain — so this leaf never has to.
 */
class Wander(
  private val home: Vec3L,
  private val locomotion: Locomotion,
  private val radius: Long,
) : BtNode {

  override fun tick(context: BtContext): Status {
    locomotion.wanderStep(context, home, radius)
    return Status.RUNNING
  }

  override fun toString(): String = "Wander(around $home, r=$radius)"
}
