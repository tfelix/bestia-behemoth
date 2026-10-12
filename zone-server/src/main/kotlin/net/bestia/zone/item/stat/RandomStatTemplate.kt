package net.bestia.zone.item.stat

/**
 * What a copy of an item may roll when it is made: one [Slot] per possible stat, and one chance per possible
 * socket. A socket only opens when every socket before it opened.
 */
data class RandomStatTemplate(
  val slots: List<Slot> = emptyList(),
  val socketChances: List<Int> = emptyList()
) {

  init {
    require(socketChances.size <= MAX_SOCKETS) { "at most $MAX_SOCKETS sockets, got ${socketChances.size}" }
    require(socketChances.all { it in 1..100 }) { "a socket chance is a percentage in 1..100: $socketChances" }
  }

  /** [min] and [max] are in the stat's own unit; [max] null means only the forge cap limits the roll. */
  data class Slot(
    val candidates: List<ItemStat>,
    val chance: Int = 100,
    val min: Int = 1,
    val max: Int? = null
  ) {

    init {
      require(candidates.isNotEmpty()) { "a slot needs at least one stat to roll" }
      require(chance in 1..100) { "a slot chance is a percentage in 1..100, got $chance" }
      require(min >= 1) { "a rolled stat is at least 1, got min $min" }
      require(max == null || max >= min) { "max $max is below min $min" }
    }
  }

  companion object {
    /** Ragnarok Online's most slotted gear has four. */
    const val MAX_SOCKETS = 4
  }
}
