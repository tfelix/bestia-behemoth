package net.bestia.zone.economy

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.core.AsyncJobExecutor
import net.bestia.zone.environment.time.BestiaClock
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.settlement.SettlementSiteIndex
import org.springframework.stereotype.Service

/**
 * The books of every settlement that has any, and the only place one is created or destroyed.
 *
 * ### Lazy catch-up is the whole design
 *
 * A settlement is brought up to date when somebody asks about it, not on a world tick, and that is the
 * same answer as ticking it every day because [EconomyStep] is a semigroup over the interval. So a town
 * nobody has visited for a week costs nothing until they walk in, and then costs one catch-up.
 *
 * ### Reading cannot create a row
 *
 * I18. Not by care at the call sites but structurally: a settlement with no row is at its reference,
 * stepping a state that is at its reference leaves it there, and [rememberIfWorthIt] only writes what is
 * outside the tolerance. Nothing a reader can do produces the first non-zero deviation - only a trade or
 * player damage does.
 *
 * Tick-thread only, like the registries it sits beside; the map is plain and unsynchronised, and the
 * durable write goes to [AsyncJobExecutor] keyed on the settlement so two updates cannot land out of
 * order.
 */
@Service
class SettlementEconomyService(
  private val catalogue: EconomyCatalogue,
  private val step: EconomyStep,
  private val damage: SettlementCapacity,
  private val sites: SettlementSiteIndex,
  private val repository: SettlementLedgerRepository,
  private val asyncJobExecutor: AsyncJobExecutor,
  private val worldService: WorldService,
  private val clock: BestiaClock,
) {

  /** Only settlements away from their reference. A town at rest is absent, exactly as its row is. */
  private val live = HashMap<Int, LedgerState>()

  private val references = HashMap<Int, SettlementReference>()

  val trackedSettlements: Int get() = live.size

  /**
   * What a settlement's shelves look like right now, or null for a place with no economy at all.
   *
   * The books are caught up on the way, because a price read off a stale deviation is a price from
   * whenever the town was last looked at.
   */
  fun marketOf(settlement: Int): SettlementMarket? {
    val reference = referenceOf(settlement) ?: return null
    val today = clock.now().absoluteDay

    return SettlementMarket(catalogue, reference, advance(settlement, reference, today), today.mod(YEAR))
  }

  /**
   * The shop window of the settlement at ([x], [y]) in position units, or null out in the country.
   *
   * The one entry point a trade uses, so the settlement a player buys from is decided by where they are
   * standing and never by anything the client sends.
   */
  fun shopAt(x: Long, y: Long): Pair<Int, Shop>? {
    val settlement = sites.siteCovering(x, y)?.index ?: return null
    val reference = referenceOf(settlement) ?: return null
    val market = marketOf(settlement) ?: return null

    return settlement to Shop(catalogue, market, reference)
  }

  /**
   * Moves [units] of a commodity and [coins] between a settlement and a player.
   *
   * I6 and I7 in one place: what the player takes leaves the ledger and what they pay enters the
   * treasury, both exactly, because there is only one statement doing each. The books are brought up to
   * date first, so a trade in a town nobody has visited for a week is settled against today's prices and
   * not last week's.
   *
   * This is one of only two ways a settlement first gets a row - the other is player damage. Everything
   * else in this class can only move a row that already exists, or delete it.
   */
  fun settle(settlement: Int, commodity: String, units: Int, coins: Long, selling: Boolean) {
    val reference = referenceOf(settlement) ?: return
    val today = clock.now().absoluteDay
    val current = advance(settlement, reference, today)

    val moved = units.toDouble() * if (selling) 1.0 else -1.0
    val paid = coins.toDouble() * if (selling) -1.0 else 1.0

    rememberIfWorthIt(
      settlement,
      reference,
      current.copy(
        deltaStock = current.deltaStock + (commodity to (current.deltaStock[commodity] ?: 0.0) + moved),
        treasury = current.treasury + paid,
      )
    )
  }

  /** The derived path this settlement's ledger is a deviation from, cached per settlement. */
  fun referenceOf(settlement: Int): SettlementReference? {
    references[settlement]?.let { return it }

    val summary = sites.siteOf(settlement)?.population ?: return null
    if (summary.population <= 0) return null

    return SettlementReference.of(
      catalogue = catalogue,
      settlement = settlement,
      population = summary.population,
      wealth = summary.wealth,
      traffic = summary.traffic,
    ).also { references[settlement] = it }
  }

  /**
   * Brings every settlement that has a row up to today, and forgets the ones that have come back.
   *
   * What stops the table growing without bound: a town a player wrecked and walked away from decays on
   * its own and deletes itself, with nobody having to remember to sweep it.
   */
  fun catchUpAll() {
    // Damaged settlements as well as the ones already written down. A town whose fields were burnt
    // while nobody was there has no row yet, and without this it would not get one until somebody
    // happened to walk in - and would then only ever be a day behind, however long the fire burnt.
    val wanted = live.keys + damage.damagedSettlements()
    if (wanted.isEmpty()) return

    val today = clock.now().absoluteDay
    for (settlement in wanted) {
      val reference = referenceOf(settlement) ?: continue
      advance(settlement, reference, today)
    }
  }

  fun loadAll() {
    val shapeVersion = worldService.record.shapeVersion
    val pipelineVersion = worldService.record.pipelineVersion

    val (valid, orphaned) = repository.findAll().partition {
      it.worldShapeVersion == shapeVersion && it.pipelineVersion == pipelineVersion
    }

    valid.forEach { live[it.settlement] = it.toState() }

    if (orphaned.isNotEmpty()) {
      // Discarded rather than skipped, on `WorldObjectDivergence`'s reasoning and more sharply: indices
      // are dense and re-used, so a surviving row would be applied to a *different* town.
      repository.deleteAll(orphaned)
      LOG.warn { "${orphaned.size} settlement ledger row(s) do not belong to this world; discarded" }
    }

    LOG.info { "Loaded ${live.size} settlement ledger(s)" }
  }

  private fun advance(settlement: Int, reference: SettlementReference, today: Double): LedgerState {
    val current = live[settlement] ?: atReference(reference, today)
    val moved = step.advance(reference, current, today)

    rememberIfWorthIt(settlement, reference, moved)

    return moved
  }

  private fun rememberIfWorthIt(settlement: Int, reference: SettlementReference, state: LedgerState) {
    val worthKeeping =
      !state.isNegligible(STOCK_TOLERANCE, PRICE_TOLERANCE, reference.treasury, TREASURY_TOLERANCE)

    if (worthKeeping) {
      live[settlement] = state
      save(settlement, state)
      return
    }

    if (live.remove(settlement) != null) {
      asyncJobExecutor.submit(settlement.toLong()) { repository.deleteById(settlement) }
      LOG.debug { "Settlement $settlement is back at its reference; ledger dropped" }
    }
  }

  /**
   * Where a settlement with no row starts: at its reference, as of one day ago.
   *
   * A day ago rather than now, and that is load bearing. Anchored at now, the first advance is always
   * zero days long - and because a settlement at its reference is not written down, the next call
   * anchors at now again, so a town that is *damaged but has no row yet* could never take its first
   * step and would sit at full price forever with its fields burnt.
   *
   * Harmless for the undamaged case, which is nearly all of them: one sub-step from zero leaves zero,
   * the state is still negligible, and no row appears. I18 holds either way.
   */
  private fun atReference(reference: SettlementReference, today: Double): LedgerState {
    return LedgerState(treasury = reference.treasury, lastStepDay = today - FIRST_STEP_DAYS)
  }

  private fun save(settlement: Int, state: LedgerState) {
    val shapeVersion = worldService.record.shapeVersion
    val pipelineVersion = worldService.record.pipelineVersion

    asyncJobExecutor.submit(settlement.toLong()) {
      repository.save(SettlementLedger.of(settlement, state, shapeVersion, pipelineVersion))
    }
  }

  companion object {
    /** Units of stock below which a settlement counts as being at its reference. */
    const val STOCK_TOLERANCE = 0.5

    /**
     * How far a purse may sit from the reference and still count as being at it, as a share of it.
     *
     * Relative, where the stock tolerance is absolute, because a treasury is thousands of coins and half
     * a coin of it is a precision nothing observes. Held absolute, how long a row survives would be set
     * by whatever time constant the purse happens to revert on rather than by whether the town is
     * actually disturbed.
     */
    const val TREASURY_TOLERANCE = 0.05

    /** In log price, so about a twentieth of a percent - well under the smallest coin. */
    const val PRICE_TOLERANCE = 5e-4

    /** How far back a settlement nobody has stepped before is assumed to have been at its reference. */
    private const val FIRST_STEP_DAYS = 1.0

    private val YEAR = Commodity.DAYS_PER_YEAR.toDouble()

    private val LOG = KotlinLogging.logger { }
  }
}
