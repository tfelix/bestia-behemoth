package net.bestia.zone.blackboard

import net.bestia.zone.blackboard.persistence.BlackboardPostingRepository
import net.bestia.zone.world.WorldRecreatedEvent
import org.springframework.context.event.EventListener
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** Postings and the pay they hold are lost with the world. */
@Component
class BlackboardCarryOver(
  private val postings: BlackboardPostingRepository,
) {

  @EventListener
  @Order(WorldRecreatedEvent.ListenerOrder.CARRY_OVER)
  @Transactional
  fun handleWorldRecreated(event: WorldRecreatedEvent) {
    if (!event.isRebirth) {
      return
    }
    postings.deleteAll()
  }
}
