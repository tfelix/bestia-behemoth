package net.bestia.zone.breeding

import net.bestia.zone.breeding.persistence.BreedingFarmRowRepository
import net.bestia.zone.breeding.persistence.IncubatorRowRepository
import net.bestia.zone.world.WorldRecreatedEvent
import org.springframework.context.event.EventListener
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** Farms and incubators are structures, lost with the world. An egg is an item and goes with the bags. */
@Component
class BreedingCarryOver(
  private val farms: BreedingFarmRowRepository,
  private val incubators: IncubatorRowRepository,
) {

  @EventListener
  @Order(WorldRecreatedEvent.ListenerOrder.CARRY_OVER)
  @Transactional
  fun handleWorldRecreated(event: WorldRecreatedEvent) {
    if (!event.isRebirth) {
      return
    }
    farms.deleteAll()
    incubators.deleteAll()
  }
}
