package net.bestia.zone.questgen.offer

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.entity.observe.WorldTarget
import net.bestia.zone.fact.FactService
import net.bestia.zone.fact.FactSource
import net.bestia.zone.quest.QuestDraft
import net.bestia.zone.quest.QuestOrigin
import net.bestia.zone.quest.QuestService
import net.bestia.zone.questgen.QuestgenConfig
import net.bestia.zone.questgen.persistence.StoredQuestPlan
import net.bestia.zone.questgen.persistence.StoredQuestPlanRepository
import net.bestia.zone.questgen.plan.QuestPlan
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap

/**
 * The plans a master can pick up now, and the picking up. A plan becomes a quest of the quest core when the
 * first master accepts it; later masters join that quest, so a unique prize is one quest and a race.
 */
@Service
class QuestOffers(
  private val plans: StoredQuestPlanRepository,
  private val quests: QuestService,
  private val facts: FactService,
  private val config: QuestgenConfig,
  private val clock: Clock = Clock.systemUTC(),
) {

  /** What a giver needs on the tick to speak of its quest. */
  class Offer(val planId: Long, val plan: QuestPlan)

  /** Read on the tick by the conversation, written off it by [refresh]. */
  private val byStart = ConcurrentHashMap<WorldTarget, Offer>()

  /** The running quests whose artifact a town wants back, by the town. Read on the tick. */
  private val byHome = ConcurrentHashMap<Int, Offer>()

  fun at(start: WorldTarget): Offer? {
    return byStart[start]
  }

  /** The running quest that ends with something brought to [settlement], if there is one. */
  fun broughtTo(settlement: Int): Offer? {
    return byHome[settlement]
  }

  /** Reads again what can be picked up. Called whenever a plan starts or stops being offered. */
  @Transactional(readOnly = true)
  fun refresh() {
    val open = plans.findAllByStateIn(StoredQuestPlan.State.entries.filter { it.canBeAccepted })
      .associate { it.plan.startsAt to Offer(it.id, it.plan) }
    byStart.keys.retainAll(open.keys)
    byStart.putAll(open)

    val awaited = plans.findAllByState(StoredQuestPlan.State.RUNNING)
      .mapNotNull { stored -> stored.plan.broughtTo?.let { it to Offer(stored.id, stored.plan) } }
      .toMap()
    byHome.keys.retainAll(awaited.keys)
    byHome.putAll(awaited)
  }

  /** @return the refusal, or null when the quest is now in [masterId]'s log. */
  @Transactional
  fun accept(masterId: Long, planId: Long): OpError? {
    val stored = plans.findLocked(planId)?.takeIf { it.state.canBeAccepted } ?: return OpError.QUEST_CLOSED
    val questId = stored.questId ?: quests.create(draftOf(stored.plan)).id.also {
      stored.questId = it
      stored.state = StoredQuestPlan.State.RUNNING
    }

    quests.accept(masterId, questId)?.let { return it }
    hearTheGiverOut(masterId, questId, stored.plan)

    return null
  }

  /** The master stands before the giver, so a first step that is "hear them out" is done as they accept. */
  private fun hearTheGiverOut(masterId: Long, questId: Long, plan: QuestPlan) {
    val first = plan.steps.first()
    if (first.objective.target != plan.startsAt) {
      return
    }

    quests.advancePersonal(masterId, questId, objectiveIndex = 0, amount = first.objective.required)
    first.teaches.forEach { facts.learn(masterId, it, FactSource.TOWNSFOLK) }
  }

  private fun draftOf(plan: QuestPlan): QuestDraft {
    return QuestDraft(
      origin = QuestOrigin.WORLD,
      title = plan.title,
      suggestedLevel = plan.suggestedLevel,
      deadline = clock.instant().plus(config.questRunsFor),
      objectives = plan.steps.map { it.objective },
      // What a quest of the world pays is decided by its ending.
      rewards = emptyList(),
    )
  }
}
