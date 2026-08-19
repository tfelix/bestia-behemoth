package net.bestia.zone.boot

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.item.container.ContainerSlotRepository
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Frees every item still marked as promised to a trade.
 *
 * This is the whole crash-recovery story for trading, and the reason offered items are marked in place
 * rather than moved into an escrow container: a trade lives only in memory, so any reservation surviving a
 * restart belongs to a trade that no longer exists. The item never left its owner, so giving it back is a
 * single column update with nothing to reconcile.
 *
 * Ordered before masters can be spawned into the world so that no live inventory is ever built from a
 * container still hiding a stale reservation.
 */
@Component
@Order(106)
class TradeReservationCleanupBootRunner(
  private val containerSlots: ContainerSlotRepository,
) : CommandLineRunner {

  @Transactional
  override fun run(vararg args: String?) {
    val freed = containerSlots.clearAllTradeReservations()

    if (freed > 0) {
      LOG.info { "Freed $freed item slot(s) left reserved by a trade that did not survive the last shutdown." }
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
