package net.bestia.zone.breeding

import net.bestia.zone.ai.core.state.Blackboard
import net.bestia.zone.ai.domain.bestia.BestiaDomain
import net.bestia.zone.ai.ecs.AiAgent
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.prop.station.WorkAssignment
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.anchor.AnchorPriority
import net.bestia.zone.world.anchor.SimulationAnchor

/**
 * Keeps a parent at its farm. A [WorkAssignment] like a station worker's, so the AI brings it home to the farm after
 * a fight or after its owner drove it, and its anchor keeps the farm's ground loaded while the owner is away.
 */
object BreedingLeash {

  /** Inside the fence. */
  private const val RADIUS = 3L

  val READS: ComponentClassSet = setOf(WorkAssignment::class, Position::class)
  val WRITES: ComponentClassSet = setOf(WorkAssignment::class, AiAgent::class, SimulationAnchor::class)

  fun isLeashedTo(world: World, bestiaId: EntityId, structureId: Long): Boolean {
    return world.get(bestiaId, WorkAssignment::class)?.structureId == structureId
  }

  /** Whether it has been given other work, such as a station, since it was leashed. */
  fun isWorkingElsewhere(world: World, bestiaId: EntityId, structureId: Long): Boolean {
    val assignment = world.get(bestiaId, WorkAssignment::class) ?: return false
    return assignment.structureId != structureId
  }

  fun attach(world: World, bestiaId: EntityId, structureId: Long, farmAt: Vec3L) {
    val memory = world.get(bestiaId, AiAgent::class)?.memory
    val formerRadius = memory?.get(BestiaDomain.WANDER_RADIUS) ?: BestiaDomain.DEFAULT_WANDER_RADIUS

    world.add(bestiaId, WorkAssignment(structureId, farmAt, formerRadius))
    memory?.set(BestiaDomain.HOME_POSITION, farmAt, Blackboard.PERMANENT)
    memory?.set(BestiaDomain.WANDER_RADIUS, RADIUS, Blackboard.PERMANENT)

    world.get(bestiaId, SimulationAnchor::class)?.let { anchor ->
      anchor.atRest = AnchorPriority.WORKER
      if (anchor.priority == AnchorPriority.RESTING) anchor.priority = AnchorPriority.WORKER
    }
  }

  /** Lets it go where it stands. Does nothing to a bestia leashed elsewhere. */
  fun release(world: World, bestiaId: EntityId, structureId: Long) {
    val assignment = world.get(bestiaId, WorkAssignment::class)?.takeIf { it.structureId == structureId } ?: return
    world.remove(bestiaId, WorkAssignment::class)

    val memory = world.get(bestiaId, AiAgent::class)?.memory
    world.get(bestiaId, Position::class)?.let { memory?.set(BestiaDomain.HOME_POSITION, it.toVec3L(), Blackboard.PERMANENT) }
    memory?.set(BestiaDomain.WANDER_RADIUS, assignment.formerWanderRadius, Blackboard.PERMANENT)

    world.get(bestiaId, SimulationAnchor::class)?.let { anchor ->
      anchor.atRest = AnchorPriority.RESTING
      if (anchor.priority == AnchorPriority.WORKER) anchor.priority = AnchorPriority.RESTING
    }
  }
}
