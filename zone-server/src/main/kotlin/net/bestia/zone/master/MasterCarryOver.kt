package net.bestia.zone.master

import net.bestia.zone.account.persistence.MasterRepository
import net.bestia.zone.account.persistence.OrderPlaceRepository
import net.bestia.zone.account.persistence.PlayerBestiaRepository
import net.bestia.zone.world.WorldRecreatedEvent
import org.springframework.context.event.EventListener
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Warp marks, the places named for bestia orders and the journeys bestia are on all point into the old world, so
 * they are lost with it.
 */
@Component
class MasterCarryOver(
  private val masters: MasterRepository,
  private val bestias: PlayerBestiaRepository,
  private val orderPlaces: OrderPlaceRepository,
) {

  @EventListener
  @Order(WorldRecreatedEvent.ListenerOrder.CARRY_OVER)
  @Transactional
  fun handleWorldRecreated(event: WorldRecreatedEvent) {
    if (!event.isRebirth) {
      return
    }
    masters.findAll().forEach { it.warpMarks = "" }
    orderPlaces.deleteAll()
    bestias.findAll()
      .filter { it.journey != null }
      .forEach { it.journey = null }
  }
}
