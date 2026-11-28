package net.bestia.zone.ai.ecs

import net.bestia.zone.ai.domain.bestia.BestiaDomain
import net.bestia.zone.ai.profile.BestiaJob
import net.bestia.zone.bestia.diary.BestiaState
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.prop.station.WorkAssignment
import net.bestia.zone.travel.Journey
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** Sorts what an owned bestia does now into a [BestiaState]. Null for one that is dead or has no mind. */
@Component
class OwnedBestiaStates(
  private val pauses: WorkplacePauses = WorkplacePauses { false },
) {

  fun of(world: World, id: EntityId): BestiaState? {
    if (world.has(id, Dead::class)) return null
    val agent = world.get(id, AiAgent::class) ?: return null
    if (world.has(id, Journey::class)) return BestiaState.TRAVELLING

    val goal = agent.currentGoal?.name
    GoalActivity.ofGoal(goal)?.let { return it.state }
    if (goal in WORK_GOALS) return BestiaState.WORKING

    val workplace = world.get(id, WorkAssignment::class)
    if (workplace != null) {
      return if (pauses.waitsForMaster(workplace.structureId)) BestiaState.PAUSED else BestiaState.WORKING
    }
    if (agent.memory.get(BestiaDomain.CHEST_FULL_TOLD) == true) return BestiaState.PAUSED

    return when (agent.memory.get(BestiaDomain.MAIN_JOB)) {
      BestiaJob.GUARD -> BestiaState.WORKING
      BestiaJob.REST -> BestiaState.RESTING
      else -> BestiaState.IDLE
    }
  }

  companion object {
    val READS: ComponentClassSet = setOf(Dead::class, AiAgent::class, Journey::class, WorkAssignment::class)

    /** On the way to work: claiming a station or shaft, or a guard walking back to its post. */
    private val WORK_GOALS = setOf("Craft", "Mine", "ReturnToMiddle")
  }
}
