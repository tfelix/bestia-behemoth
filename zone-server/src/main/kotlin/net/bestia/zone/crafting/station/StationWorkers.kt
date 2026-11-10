package net.bestia.zone.crafting.station

import net.bestia.zone.ai.core.state.Blackboard
import net.bestia.zone.ai.domain.bestia.BestiaDomain
import net.bestia.zone.ai.ecs.AiAgent
import net.bestia.zone.ecs.core.World
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.prop.PlayerStructureRegistry
import net.bestia.zone.prop.StructureEntry
import net.bestia.zone.prop.station.WorkAssignment
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.anchor.AnchorPriority
import net.bestia.zone.world.anchor.SimulationAnchor
import org.springframework.stereotype.Component

/**
 * Which bestia works which station, and what that does to the bestia.
 *
 * A working bestia's home is its station and it strays no further than [WORK_RADIUS], so its own AI walks it back
 * after a fight or a meal. Its anchor keeps its ground loaded while its master is away.
 */
@Component
class StationWorkers(
  private val structures: PlayerStructureRegistry,
) {

  /** Structure id -> the entity working it, for every station a bestia works. */
  private val byStation = HashMap<Long, EntityId>()

  fun entityOf(structureId: Long): EntityId? {
    return byStation[structureId]
  }

  /** Sets [bestiaId] to work [station], taking it off any other station first. */
  fun assign(world: World, bestiaId: EntityId, station: StructureEntry) {
    val playerBestiaId = world.get(bestiaId, OwnedBestia::class)?.playerBestiaId ?: return

    world.get(bestiaId, WorkAssignment::class)?.let { release(world, bestiaId) }
    byStation[station.id]?.let { release(world, it) }

    attach(world, bestiaId, station)
    structures.setWorker(station.id, playerBestiaId)
  }

  /** Takes the bestia working [structureId] off it, so its owner works it again. */
  fun unassign(world: World, structureId: Long) {
    byStation[structureId]?.let { release(world, it) }
    structures.setWorker(structureId, null)
  }

  /** Puts a bestia that comes into the world back to the work it was doing. */
  fun resume(world: World, bestiaId: EntityId) {
    val playerBestiaId = world.get(bestiaId, OwnedBestia::class)?.playerBestiaId ?: return
    val station = structures.workplaceOf(playerBestiaId) ?: return

    attach(world, bestiaId, station)
  }

  private fun attach(world: World, bestiaId: EntityId, station: StructureEntry) {
    val memory = world.get(bestiaId, AiAgent::class)?.memory
    val formerRadius = memory?.get(BestiaDomain.WANDER_RADIUS) ?: BestiaDomain.DEFAULT_WANDER_RADIUS

    world.add(bestiaId, WorkAssignment(station.id, station.position, formerRadius))
    memory?.set(BestiaDomain.HOME_POSITION, station.position, Blackboard.PERMANENT)
    memory?.set(BestiaDomain.WANDER_RADIUS, WORK_RADIUS, Blackboard.PERMANENT)

    world.get(bestiaId, SimulationAnchor::class)?.let { anchor ->
      anchor.atRest = AnchorPriority.WORKER
      if (anchor.priority == AnchorPriority.RESTING) anchor.priority = AnchorPriority.WORKER
    }

    byStation[station.id] = bestiaId
  }

  private fun release(world: World, bestiaId: EntityId) {
    val assignment = world.get(bestiaId, WorkAssignment::class) ?: return
    world.remove(bestiaId, WorkAssignment::class)
    byStation.remove(assignment.structureId)

    // Home is where it stands now, as when its orders change: it does not walk back to where it lived before.
    val memory = world.get(bestiaId, AiAgent::class)?.memory
    world.get(bestiaId, Position::class)?.let { memory?.set(BestiaDomain.HOME_POSITION, it.toVec3L(), Blackboard.PERMANENT) }
    memory?.set(BestiaDomain.WANDER_RADIUS, assignment.formerWanderRadius, Blackboard.PERMANENT)

    world.get(bestiaId, SimulationAnchor::class)?.let { anchor ->
      anchor.atRest = AnchorPriority.RESTING
      if (anchor.priority == AnchorPriority.WORKER) anchor.priority = AnchorPriority.RESTING
    }

    structures.of(assignment.structureId)?.takeIf { it.workerBestiaId != null }
      ?.let { structures.setWorker(it.id, null) }
  }

  companion object {
    /** Inside the station's reach, so a working bestia that stays home is close enough to work. */
    const val WORK_RADIUS = 2L
  }
}
