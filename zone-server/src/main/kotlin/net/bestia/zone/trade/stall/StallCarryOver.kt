package net.bestia.zone.trade.stall

import net.bestia.zone.trade.persistence.StreetStallListingRepository
import net.bestia.zone.world.WorldRecreatedEvent
import org.springframework.context.event.EventListener
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** Goods on a street stall are lost with the world, rather than posted back to the seller. */
@Component
class StallCarryOver(
  private val listings: StreetStallListingRepository,
) {

  @EventListener
  @Order(WorldRecreatedEvent.ListenerOrder.CARRY_OVER)
  @Transactional
  fun handleWorldRecreated(event: WorldRecreatedEvent) {
    if (!event.isRebirth) {
      return
    }
    listings.deleteAll()
  }
}
