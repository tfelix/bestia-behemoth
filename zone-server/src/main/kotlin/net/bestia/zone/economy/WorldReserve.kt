package net.bestia.zone.economy

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.core.AsyncJobExecutor
import net.bestia.zone.world.WorldRecreatedEvent
import net.bestia.zone.world.WorldService
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import kotlin.math.max

/**
 * The counterparty that makes the coin supply finite.
 *
 * A settlement's books move for reasons that are not a player: it reverts toward what a town that size
 * ought to hold, and it trades with the world beyond the map. Both used to conjure the coin, which is
 * what made the supply notional. They are now drawn from here and paid back here, so every coin a
 * settlement gains is one the reserve no longer has.
 *
 * Held in memory and flushed on the economy sweep rather than written per change. The reserve moves on
 * every catch-up of every town, and `ShopTradeIntentSystem` already states what a crash may cost: under
 * a minute of trades replayed as though they had not happened.
 */
@Service
class WorldReserve(
  private val money: WorldMoneySupply,
  private val repository: WorldTreasuryRepository,
  private val worldService: WorldService,
  private val asyncJobExecutor: AsyncJobExecutor,
) : CoinReserve {

  private var balance: Double = 0.0

  private var dirty: Boolean = false

  /** Never negative: a shortfall is the reserve being empty, not the world owing itself money. */
  override fun available(): Double {
    return max(0.0, balance)
  }

  /**
   * Moves [coins] out of the reserve and into whoever gained them - or back, for a negative amount.
   *
   * The caller has already moved the other side. This is the half that keeps the total constant, so it
   * must be called for every gain that did not come from another purse.
   */
  override fun charge(coins: Double) {
    if (coins == 0.0) return

    balance -= coins
    dirty = true
  }

  fun flush() {
    if (!dirty) return

    val saved = balance
    dirty = false
    asyncJobExecutor.submit(JOB_KEY) {
      repository.save(
        WorldTreasury(
          reserve = saved,
          worldShapeVersion = worldService.record.shapeVersion,
          pipelineVersion = worldService.record.pipelineVersion,
        )
      )
    }
  }

  fun load() {
    val stored = repository.findById(WorldTreasury.SINGLETON).orElse(null)
    val belongsHere = stored != null &&
      stored.worldShapeVersion == worldService.record.shapeVersion &&
      stored.pipelineVersion == worldService.record.pipelineVersion

    if (belongsHere) {
      balance = stored.reserve
      LOG.info { "World reserve: ${balance.toLong()} coin(s) not yet handed out" }
      return
    }

    if (stored != null) {
      // Discarded rather than carried over: a reserve counted against a different world's treasuries is
      // not a smaller number, it is a meaningless one.
      repository.deleteAll()
      LOG.warn { "The world reserve does not belong to this world; started again from the unminted supply" }
    }

    reset()
  }

  @EventListener
  fun handleWorldRecreated(event: WorldRecreatedEvent) {
    reset()
  }

  private fun reset() {
    balance = money.unminted
    dirty = true
    LOG.info { "World reserve opens at ${balance.toLong()} coin(s), all of it still in the ground" }
  }

  private companion object {
    /** One key, so the flushes of a single row cannot land out of order. */
    const val JOB_KEY = 0L

    val LOG = KotlinLogging.logger { }
  }
}
