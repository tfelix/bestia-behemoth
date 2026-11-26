package net.bestia.zone.spellbinding

import net.bestia.zone.spellbinding.persistence.SpellBindingRepository
import net.bestia.zone.world.WorldRecreatedEvent
import org.springframework.context.event.EventListener
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** A bound spell waits on ground or an item, and both are lost with the world. */
@Component
class SpellBindingCarryOver(
  private val bindings: SpellBindingRepository,
) {

  @EventListener
  @Order(WorldRecreatedEvent.ListenerOrder.CARRY_OVER)
  @Transactional
  fun handleWorldRecreated(event: WorldRecreatedEvent) {
    if (!event.isRebirth) {
      return
    }
    bindings.deleteAll()
  }
}
