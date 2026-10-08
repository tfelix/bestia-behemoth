package net.bestia.zone.world.settlement

/** Which settlements have fallen: every one of their buildings destroyed, whatever destroyed them. */
interface SettlementFates {

  fun hasFallen(settlement: Int): Boolean

  /** Every settlement fallen so far. */
  fun fallen(): Set<Int>

  /** Registers a callback for each settlement that falls from now on. It runs on the tick; mark and return. */
  fun onFell(listener: (settlement: Int) -> Unit)
}
