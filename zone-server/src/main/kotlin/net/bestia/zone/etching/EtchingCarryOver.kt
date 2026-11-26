package net.bestia.zone.etching

import net.bestia.zone.account.persistence.PlayerBestiaRepository
import net.bestia.zone.etching.persistence.EtchingJobRepository
import net.bestia.zone.world.WorldRecreatedEvent
import org.springframework.context.event.EventListener
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * The Carving Tables are lost with the world, and so is the work on them. A bestia locked in one for an etching is
 * not: it comes back to its master unchanged.
 */
@Component
class EtchingCarryOver(
  private val jobs: EtchingJobRepository,
  private val bestias: PlayerBestiaRepository,
) {

  @EventListener
  @Order(WorldRecreatedEvent.ListenerOrder.CARRY_OVER)
  @Transactional
  fun handleWorldRecreated(event: WorldRecreatedEvent) {
    if (!event.isRebirth) {
      return
    }
    jobs.deleteAll()
    bestias.findAll()
      .filter { it.isDeposited }
      .forEach { it.depositedAtStructureId = null }
  }
}
