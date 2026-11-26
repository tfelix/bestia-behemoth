package net.bestia.zone.battle

import net.bestia.zone.battle.persistence.PersistedStatusEffectRepository
import net.bestia.zone.world.WorldRecreatedEvent
import org.springframework.context.event.EventListener
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** Nobody carries a buff or a curse into a new world. */
@Component
class StatusEffectCarryOver(
  private val effects: PersistedStatusEffectRepository,
) {

  @EventListener
  @Order(WorldRecreatedEvent.ListenerOrder.CARRY_OVER)
  @Transactional
  fun handleWorldRecreated(event: WorldRecreatedEvent) {
    if (!event.isRebirth) {
      return
    }
    effects.deleteAll()
  }
}
