package net.bestia.zone.blackboard

/** The board's fee, rounded up to a whole coin and never less than one, like every fee in the game. */
object BlackboardFees {

  fun feeFor(pay: Long, percent: Int): Long {
    return maxOf(1L, (pay * percent + 99) / 100)
  }
}
