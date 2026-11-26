package net.bestia.zone.travel

import net.bestia.zone.notebook.NotebookService
import net.bestia.zone.world.WorldRecreatedEvent
import org.springframework.context.event.EventListener
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** Saved routes lead through the old world, so they are lost with it. */
@Component
class RouteCarryOver(
  private val notebook: NotebookService,
) {

  @EventListener
  @Order(WorldRecreatedEvent.ListenerOrder.CARRY_OVER)
  @Transactional
  fun handleWorldRecreated(event: WorldRecreatedEvent) {
    if (!event.isRebirth) {
      return
    }
    notebook.clearAll(RoutesCollection.ID)
  }
}
