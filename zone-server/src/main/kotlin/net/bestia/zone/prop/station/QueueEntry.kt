package net.bestia.zone.prop.station

/** One line of a station's queue: [amount] of a recipe, [done] of which are made or failed already. */
data class QueueEntry(
  val recipeId: Long,
  val amount: Int,
  val done: Int = 0
) {
  init {
    require(amount > 0 && done in 0..<amount) { "A queue entry needs 0 <= done < amount, got $done of $amount" }
  }

  val left: Int
    get() {
      return amount - done
    }
}
