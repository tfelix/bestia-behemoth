package net.bestia.zone.questgen

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.questgen.persistence.StoredQuestPlan
import net.bestia.zone.questgen.persistence.StoredQuestPlanRepository
import net.bestia.zone.questgen.plan.BindSlot
import net.bestia.zone.questgen.plan.QuestCatalogue
import net.bestia.zone.questgen.plan.QuestPlanner
import net.bestia.zone.questgen.plan.QuestWorld
import net.bestia.zone.questgen.seed.QuestSeed
import net.bestia.zone.questgen.seed.QuestSeedSource
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Plans every seed that has no plan yet and stores what comes out. The one writer of `quest_plan`: on a cluster
 * a single node holds this job. Off the tick.
 */
@Service
class QuestPlanning(
  private val sources: List<QuestSeedSource>,
  private val plans: StoredQuestPlanRepository,
  catalogue: QuestCatalogue,
  world: QuestWorld,
) {

  private val planner = QuestPlanner(catalogue, world)

  /** What one run did: the new plans, and for each seed without one what the world was missing. */
  class Report(val planned: List<StoredQuestPlan>, val unplannable: Map<QuestSeed, Set<BindSlot>>)

  @Transactional
  fun planNewSeeds(): Report {
    val stored = plans.findAll()
    val known = stored.map { it.plan.seed.kind to it.plan.seed.subject }.toSet()
    val taken = stored.flatMap { targetsOf(it) }.toMutableSet()

    val planned = ArrayList<StoredQuestPlan>()
    val unplannable = LinkedHashMap<QuestSeed, Set<BindSlot>>()
    // In a fixed order, because each plan avoids what the plans before it point at.
    val seeds = sources.flatMap { it.seeds() }
      .filterNot { (it.kind to it.subject) in known }
      .sortedWith(compareBy({ it.kind }, { it.subject.type }, { it.subject.index }))
    for (seed in seeds) {
      val outcome = planner.plan(seed, taken)
      val plan = outcome.plan
      if (plan == null) {
        unplannable[seed] = outcome.missing
        continue
      }

      val saved = plans.save(StoredQuestPlan(plan))
      taken += targetsOf(saved)
      planned += saved
    }

    LOG.info { "Planned ${planned.size} quests; ${unplannable.size} seeds have no plan: ${missingCounts(unplannable)}" }

    return Report(planned, unplannable)
  }

  private fun targetsOf(stored: StoredQuestPlan) = stored.plan.steps.mapNotNull { it.objective.target }

  private fun missingCounts(unplannable: Map<QuestSeed, Set<BindSlot>>): Map<BindSlot, Int> {
    return unplannable.values.flatten().groupingBy { it }.eachCount()
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
