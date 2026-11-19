package net.bestia.zone.questgen.offer

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.entity.observe.WorldTarget
import net.bestia.zone.quest.QuestService
import net.bestia.zone.questgen.QuestgenConfig
import net.bestia.zone.questgen.persistence.StoredQuestPlan
import net.bestia.zone.questgen.persistence.StoredQuestPlan.State
import net.bestia.zone.questgen.persistence.StoredQuestPlanRepository
import net.bestia.zone.questgen.seed.QuestSeedSource
import net.bestia.zone.townsfolk.ecs.TownsfolkIdentity
import net.bestia.zone.world.settlement.SettlementSiteIndex
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Decides which plans are offered now. A town offers a few quests at a time, by its size, and a plan is only
 * offered while its cause is real. Off the tick; on a cluster a single node holds this job.
 */
@Service
class QuestDirector(
  private val plans: StoredQuestPlanRepository,
  private val sources: List<QuestSeedSource>,
  private val sites: SettlementSiteIndex,
  private val offers: QuestOffers,
  private val quests: QuestService,
  private val config: QuestgenConfig,
) {

  @Scheduled(fixedDelay = RUN_EVERY_MS, initialDelay = RUN_EVERY_MS)
  @Transactional
  fun run() {
    val expired = planAgainAfterDeadline()
    val withdrawn = withdrawWithoutCause()
    val offered = offerWithinBudget()
    if (expired + withdrawn + offered > 0) {
      LOG.info { "Offered $offered quests, withdrew $withdrawn whose cause is gone, $expired ran out of time" }
    }
    offers.refresh()
  }

  /** Nobody finished in time, so the quest ends and its plan waits to be offered again while its cause is real. */
  private fun planAgainAfterDeadline(): Int {
    val overdue = plans.findAllByState(State.RUNNING).filter { plan -> plan.questId?.let(quests::expireIfOverdue) == true }
    overdue.forEach {
      it.questId = null
      it.state = State.PLANNED
    }

    return overdue.size
  }

  /** A plan nobody took yet ends when its cause is gone. One that runs is ended by its own ending. */
  private fun withdrawWithoutCause(): Int {
    val real = sources.flatMap { it.seeds() }.map { it.kind to it.subject }.toSet()
    val stale = plans.findAllByStateIn(listOf(State.PLANNED, State.OFFERED))
      .filterNot { (it.plan.seed.kind to it.plan.seed.subject) in real }
    stale.forEach { it.state = State.ENDED }

    return stale.size
  }

  private fun offerWithinBudget(): Int {
    val offeredPerTown = plans.findAllByStateIn(listOf(State.OFFERED, State.RUNNING))
      .mapNotNull { townOf(it) }
      .groupingBy { it }
      .eachCount()
      .toMutableMap()

    // The best plans first, so a town with more plans than room offers its best.
    val waiting = plans.findAllByState(State.PLANNED).sortedWith(compareByDescending<StoredQuestPlan> { it.plan.score }.thenBy { it.id })
    var offered = 0
    for (plan in waiting) {
      val town = townOf(plan) ?: continue
      if (offeredPerTown.getOrDefault(town, 0) >= budgetOf(town)) {
        continue
      }

      plan.state = State.OFFERED
      offeredPerTown.merge(town, 1, Int::plus)
      offered++
    }

    return offered
  }

  private fun budgetOf(town: Int): Int {
    val people = sites.siteOf(town)?.population?.population ?: 0

    return config.offersPerTown + people / config.peoplePerExtraOffer
  }

  /** The town a plan starts in. Null for a plan that starts at a thing: nothing offers those yet. */
  private fun townOf(stored: StoredQuestPlan): Int? {
    val start = stored.plan.startsAt

    return start.key.takeIf { start.kind == WorldTarget.Kind.TOWNSFOLK }?.let { TownsfolkIdentity.settlementOf(it.toLong()) }
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
    const val RUN_EVERY_MS = 5 * 60 * 1000L
  }
}
