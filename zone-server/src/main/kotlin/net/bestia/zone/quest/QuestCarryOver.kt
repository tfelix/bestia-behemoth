package net.bestia.zone.quest

import net.bestia.zone.quest.persistence.QuestLogEntryRepository
import net.bestia.zone.quest.persistence.QuestRepository
import net.bestia.zone.world.WorldRecreatedEvent
import org.springframework.context.event.EventListener
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** Quests belong to the world they were given in, and so does every master's progress on them. */
@Component
class QuestCarryOver(
  private val logEntries: QuestLogEntryRepository,
  private val quests: QuestRepository,
) {

  @EventListener
  @Order(WorldRecreatedEvent.ListenerOrder.CARRY_OVER)
  @Transactional
  fun handleWorldRecreated(event: WorldRecreatedEvent) {
    if (!event.isRebirth) {
      return
    }
    logEntries.deleteAll()
    quests.deleteAll()
  }
}
