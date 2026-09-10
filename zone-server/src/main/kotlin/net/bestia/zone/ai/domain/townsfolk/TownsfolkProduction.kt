package net.bestia.zone.ai.domain.townsfolk

import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

/**
 * What the workers a player can actually see have turned out today.
 *
 * A tally, and deliberately nothing more. It does **not** feed the settlement's books: the ledger is the
 * only producer, so a baker at a bench is rendering output the reference throughput already accounts
 * for, exactly as a villager at a stall is rendering a meal it already accounts for. Putting these
 * loaves on the shelves would make a watched town richer than an unwatched one, which is the thing
 * [net.bestia.zone.economy.ProductionBudget] exists to rule out.
 *
 * What it is for is being able to see the work happening - `/sites` reads it - and giving a shift
 * something real to show for itself when the mill has grain and nothing to show when it does not.
 */
@Service
class TownsfolkProduction {

  private data class Bench(val settlement: Int, val commodity: String)

  private val made = ConcurrentHashMap<Bench, Int>()

  @Volatile
  private var day = Long.MIN_VALUE

  fun record(settlement: Int, commodity: String, today: Long) {
    rollOver(today)
    made.merge(Bench(settlement, commodity), 1, Int::plus)
  }

  fun madeOn(settlement: Int, commodity: String, today: Long): Int {
    if (today != day) return 0

    return made[Bench(settlement, commodity)] ?: 0
  }

  /** Yesterday's work is not today's, and nothing else would ever clear this. */
  private fun rollOver(today: Long) {
    if (today == day) return

    made.clear()
    day = today
  }
}
