package net.bestia.zone.fishing

import java.util.Random
import kotlin.math.max
import kotlin.math.pow

/**
 * What bites. The rod filters, the master's catch level cap weights, and one draw picks.
 */
class CatchTable(
  private val entries: List<Entry>,
  private val overCapHalvingLevels: Int,
) {

  data class Entry(val itemId: Long, val level: Int, val weight: Int)

  init {
    require(overCapHalvingLevels > 0) { "over-cap-halving-levels must be positive, got $overCapHalvingLevels" }
    entries.forEach { require(it.weight > 0) { "Catch ${it.itemId} has weight ${it.weight}" } }
  }

  /** Null only when the rod reaches no entry at all. */
  fun draw(maxCatchLevel: Int, levelCap: Int, random: Random): Entry? {
    val reachable = entries.filter { it.level <= maxCatchLevel }
    if (reachable.isEmpty()) {
      return null
    }

    var roll = random.nextDouble() * reachable.sumOf { weightOf(it, levelCap) }
    for (entry in reachable) {
      roll -= weightOf(entry, levelCap)
      if (roll < 0.0) {
        return entry
      }
    }

    return reachable.last()
  }

  fun weightOf(entry: Entry, levelCap: Int): Double {
    val levelsOverCap = max(0, entry.level - levelCap)

    return entry.weight * 0.5.pow(levelsOverCap / overCapHalvingLevels.toDouble())
  }
}
