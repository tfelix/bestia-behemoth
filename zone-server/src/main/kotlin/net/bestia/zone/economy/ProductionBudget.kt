package net.bestia.zone.economy

import org.springframework.stereotype.Service

/**
 * What visible workers have already made since the ledger last stepped.
 *
 * The ledger is the only producer, always - a baker a player can watch is *rendering* production the
 * ledger has already accounted for. This is how the two stay in step without the animation and the
 * continuous model having to agree numerically: whatever a visible worker claims is deducted here, so a
 * watched town and an unwatched one produce the same amount.
 */
fun interface ProductionBudget {

  /** Units of [commodity] claimed by visible workers, to be taken off the ledger's own output. */
  fun claimed(settlement: Int, commodity: String): Double
}

/** No townsperson produces anything yet, so the ledger produces all of it. */
@Service
class UnclaimedProduction : ProductionBudget {
  override fun claimed(settlement: Int, commodity: String): Double {
    return 0.0
  }
}
