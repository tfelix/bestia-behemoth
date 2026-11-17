package net.bestia.zone.blackboard

import net.bestia.zone.blackboard.ecs.Blackboard
import net.bestia.zone.blackboard.ecs.BlackboardOffer
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.quest.QuestChangedEvent
import net.bestia.zone.quest.QuestOrigin
import net.bestia.zone.quest.QuestService
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * Keeps every board's [BlackboardOffer] in step with the open quests. One query per quest change and a write to
 * a few dozen boards; a board's component only syncs when its mask actually changed.
 */
@Component
class BlackboardOffers(
  private val quests: QuestService,
  private val world: WorldView,
) {

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  fun onQuestChanged(event: QuestChangedEvent) {
    refresh()
  }

  fun refresh() {
    val levels = quests.openLevels(QuestOrigin.BLACKBOARD)
    world.post {
      query(Blackboard::class, BlackboardOffer::class).each { get<BlackboardOffer>().offer(levels) }
    }
  }
}
