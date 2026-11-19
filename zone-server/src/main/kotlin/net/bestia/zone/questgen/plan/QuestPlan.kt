package net.bestia.zone.questgen.plan

import com.fasterxml.jackson.annotation.JsonIgnore
import net.bestia.zone.entity.observe.WorldTarget
import net.bestia.zone.quest.ObjectiveKind
import net.bestia.zone.fact.FactRef
import net.bestia.zone.quest.QuestObjective
import net.bestia.zone.questgen.seed.QuestSeed
import net.bestia.zone.text.TextRef

/**
 * One quest the world could offer, fully worked out: where it starts, the steps, and what each step points at.
 * Every target is an id of the generated world, never a live entity, so a plan is still good after a restart.
 */
data class QuestPlan(
  val seed: QuestSeed,
  /** The motif of `quest-motifs.yml` the plan was built from. */
  val motif: String,
  val title: TextRef,
  /** Where a master picks the quest up: the person or the thing of its first step. */
  val startsAt: WorldTarget,
  val suggestedLevel: Int,
  val steps: List<Step>,
  /** How good the planner found it; higher is better. Kept for the census. */
  val score: Double,
) {

  /** The step that brings something to a town, when the plan has one. */
  @get:JsonIgnore
  val bringStep: IndexedValue<Step>?
    get() = steps.withIndex().firstOrNull {
      it.value.objective.kind == ObjectiveKind.BRING && it.value.objective.target?.kind == WorldTarget.Kind.SETTLEMENT
    }

  /** The index of the town the plan's last step brings something to, or null. */
  @get:JsonIgnore
  val broughtTo: Int?
    get() = bringStep?.value?.objective?.target?.key?.toInt()

  /**
   * [objective] is what the quest core is handed. [teaches] is what a master knows after the step, for the
   * fact store.
   */
  data class Step(
    /** The step pattern of `quest-steps.yml`. */
    val pattern: String,
    val objective: QuestObjective,
    val teaches: List<FactRef> = emptyList(),
  )
}
