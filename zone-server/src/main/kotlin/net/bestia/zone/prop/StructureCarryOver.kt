package net.bestia.zone.prop

import net.bestia.zone.prop.persistence.PlayerStructureRepository
import net.bestia.zone.world.WorldRecreatedEvent
import org.springframework.context.event.EventListener
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** Every structure players built is lost with the world, and what their chests held. */
@Component
class StructureCarryOver(
  private val structures: PlayerStructureRepository,
) {

  @EventListener
  @Order(WorldRecreatedEvent.ListenerOrder.CARRY_OVER)
  @Transactional
  fun handleWorldRecreated(event: WorldRecreatedEvent) {
    if (!event.isRebirth) {
      return
    }
    structures.deleteAll()
  }
}
