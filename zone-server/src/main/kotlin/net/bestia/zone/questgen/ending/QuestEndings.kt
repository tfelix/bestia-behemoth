package net.bestia.zone.questgen.ending

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.worldgen.core.ActorType
import net.bestia.worldgen.core.EventKind
import net.bestia.zone.chronicle.WorldFactRecordedEvent
import net.bestia.zone.persistence.afterCommit
import net.bestia.zone.quest.QuestService
import net.bestia.zone.quest.QuestStageDoneEvent
import net.bestia.zone.quest.QuestStatus
import net.bestia.zone.questgen.offer.QuestOffers
import net.bestia.zone.questgen.persistence.StoredQuestPlan.State
import net.bestia.zone.questgen.persistence.StoredQuestPlanRepository
import net.bestia.zone.questgen.seed.QuestSeed
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Ends the quests of the world. Both listeners run inside the transaction that published the event, so a quest
 * and its plan end together with what ended them.
 */
@Component
class QuestEndings(
  private val plans: StoredQuestPlanRepository,
  private val quests: QuestService,
  private val offers: QuestOffers,
) {

  /** An artifact somebody took is no longer lost: nobody can take up its quest, and for the others it is over. */
  @EventListener
  fun onWorldFact(event: WorldFactRecordedEvent) {
    val artifact = event.actors.first().takeIf { event.kind == EventKind.ARTIFACT_TAKEN && it.type == ActorType.ARTIFACT } ?: return
    val finder = event.by

    plans.findAllBySeedKindAndSubjectTypeAndSubjectIndex(QuestSeed.Kind.LOST_RELIC, ActorType.ARTIFACT, artifact.index)
      .forEach { plan ->
        val questId = plan.questId
        val finderIsInQuest = questId != null && finder != null && finder.masterId in quests.holdersOf(questId)
        if (questId != null) {
          quests.supersede(questId, finder?.masterId, finder?.name ?: "")
        }
        // A finder who is in the quest ends the plan with their last step, which may still be ahead.
        if (!finderIsInQuest && plan.state != State.ENDED) {
          plan.state = State.ENDED
        }
      }
    afterCommit { offers.refresh() }
  }

  /** The master who did the last step has finished the quest. In a race that is the one who took the prize. */
  @EventListener
  fun onStageDone(event: QuestStageDoneEvent) {
    if (!event.wasLast) {
      return
    }
    val plan = plans.findByQuestId(event.questId) ?: return

    quests.end(event.questId, QuestStatus.COMPLETED)
    plan.state = State.ENDED
    LOG.info { "Master ${event.masterId} finished the ${plan.plan.motif} quest of ${plan.plan.seed.subject}, choice: ${event.choice}" }
    afterCommit { offers.refresh() }
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
