package net.bestia.zone.ai.domain.bestia.action

import net.bestia.zone.ai.bt.Locomotion
import net.bestia.zone.ai.bt.leaves.MoveTo
import net.bestia.zone.ai.bt.leaves.Wait
import net.bestia.zone.ai.bt.sequence
import net.bestia.zone.ai.core.action.Action
import net.bestia.zone.ai.core.action.ActionTemplate
import net.bestia.zone.ai.core.behavior.BtContext
import net.bestia.zone.ai.core.behavior.BtNode
import net.bestia.zone.ai.core.behavior.Status
import net.bestia.zone.ai.core.state.WorldState
import net.bestia.zone.ai.domain.bestia.BestiaDomain
import net.bestia.zone.ai.domain.bestia.Gatherable
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.prop.ecs.CollectPropIntent

/**
 * Walks to a collectible a gatherer saw and picks it up the way a player's click does, grounding
 * [BestiaDomain.Goals.GATHER]. One action per prop, nearest [maxCandidates] first, so cost picks the closest.
 */
class CollectPropActionTemplate(
  private val locomotion: Locomotion,
  private val maxCandidates: Int = 3,
) : ActionTemplate {
  override val id = "collectProp"

  override fun ground(state: WorldState): List<Action> {
    val position = state.get(BestiaDomain.POSITION) ?: return emptyList()
    val seen = state.get(BestiaDomain.GATHERABLES) ?: return emptyList()

    return seen
      .filter { it.work == Gatherable.Work.COLLECT }
      .sortedBy { it.position.distance(position) }
      .take(maxCandidates)
      .map { prop ->
        Action(
          name = "collectProp(${prop.propId})",
          effects = GatherWork.effectsOf(prop),
          cost = { prop.position.distance(position).toFloat() + 1f },
          // The pause is the bending down, and gives the intent the tick it needs to land before the next plan.
          behavior = {
            GatherWork.attempt(prop, sequence {
              +MoveTo(prop.position, locomotion, arrivalRadius = REACH)
              +Collect(prop)
              +Wait(COLLECT_SECONDS)
            })
          },
        )
      }
  }

  /**
   * Raises the same intent a player's click does; quiet, as nobody asked for it. Once only: the sequence ticks it
   * again during the pause, when the prop is already gone.
   */
  private class Collect(private val prop: Gatherable) : BtNode {
    private var raised = false

    override fun tick(context: BtContext): Status {
      if (raised) return Status.SUCCESS
      if (!GatherWork.stillStands(context, prop)) return Status.FAILURE

      context.world.add(context.entityId, CollectPropIntent(prop.entityId, quiet = true))
      raised = true
      return Status.SUCCESS
    }

    override fun toString(): String = "Collect(${prop.propId})"
  }

  companion object {
    /** Inside `CollectPropIntentSystem`'s range of three, without having to stand on the prop. */
    private const val REACH = 2L
    private const val COLLECT_SECONDS = 1f

    val WRITES: ComponentClassSet = setOf(CollectPropIntent::class)
  }
}
