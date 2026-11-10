package net.bestia.zone.prop.station

/**
 * How a station's queue and held items are written into their two text columns.
 *
 * Text rather than rows, the way a journey is stored on its bestia: nothing else ever queries them, and both are
 * read only once, at boot.
 */
object StationContents {

  /** `recipe:amount:done` per entry, `;` between. */
  fun encodeQueue(queue: List<QueueEntry>): String? {
    if (queue.isEmpty()) return null

    return queue.joinToString(";") { "${it.recipeId}:${it.amount}:${it.done}" }
  }

  fun decodeQueue(text: String?): List<QueueEntry> {
    if (text.isNullOrBlank()) return emptyList()

    return text.split(";").map { line ->
      val (recipeId, amount, done) = line.split(":")
      QueueEntry(recipeId.toLong(), amount.toInt(), done.toInt())
    }
  }

  /** `item:amount` per pile, `;` between. */
  fun encodeHeld(held: Map<Long, Int>): String? {
    val piles = held.filterValues { it > 0 }
    if (piles.isEmpty()) return null

    return piles.entries.joinToString(";") { "${it.key}:${it.value}" }
  }

  fun decodeHeld(text: String?): Map<Long, Int> {
    if (text.isNullOrBlank()) return emptyMap()

    return text.split(";").associate { pile ->
      val (itemId, amount) = pile.split(":")
      itemId.toLong() to amount.toInt()
    }
  }
}
