package net.bestia.zone.settlement

import net.bestia.zone.settlement.persistence.PlayerSettlementRepository
import net.bestia.zone.world.WorldRecreatedEvent
import org.springframework.context.event.EventListener
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** Player towns are lost with the world. They are founded again in the next one. */
@Component
class SettlementCarryOver(
  private val settlements: PlayerSettlementRepository,
) {

  @EventListener
  @Order(WorldRecreatedEvent.ListenerOrder.CARRY_OVER)
  @Transactional
  fun handleWorldRecreated(event: WorldRecreatedEvent) {
    if (!event.isRebirth) {
      return
    }
    settlements.deleteAll()
  }
}
