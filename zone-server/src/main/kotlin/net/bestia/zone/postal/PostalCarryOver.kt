package net.bestia.zone.postal

import net.bestia.zone.account.persistence.MasterRepository
import net.bestia.zone.postal.persistence.LetterRepository
import net.bestia.zone.postal.persistence.ParcelRepository
import net.bestia.zone.world.WorldRecreatedEvent
import org.springframework.context.event.EventListener
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Mail does not reach into a new world: letters and parcels are lost, and the goods in them. A home post office is
 * a town of the old world, so masters choose one again.
 */
@Component
class PostalCarryOver(
  private val letters: LetterRepository,
  private val parcels: ParcelRepository,
  private val masters: MasterRepository,
) {

  @EventListener
  @Order(WorldRecreatedEvent.ListenerOrder.CARRY_OVER)
  @Transactional
  fun handleWorldRecreated(event: WorldRecreatedEvent) {
    if (!event.isRebirth) {
      return
    }
    letters.deleteAll()
    parcels.deleteAll()
    masters.findAll().forEach { it.homePostOffice = null }
  }
}
