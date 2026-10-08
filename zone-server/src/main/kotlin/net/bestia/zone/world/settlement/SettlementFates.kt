package net.bestia.zone.world.settlement

/** Which settlements have fallen: every one of their buildings destroyed, whatever destroyed them. */
interface SettlementFates {

  fun hasFallen(settlement: Int): Boolean

  /** Every settlement fallen so far. */
  fun fallen(): Set<Int>

  /** Registers a callback for each settlement that falls from now on. It runs on the tick, inside the fall. */
  fun onFell(listener: (settlement: Int) -> Unit)

  companion object {
    /** No settlement ever falls. Keeps tests of the consumers short. */
    val NONE = object : SettlementFates {
      override fun hasFallen(settlement: Int): Boolean {
        return false
      }

      override fun fallen(): Set<Int> {
        return emptySet()
      }

      override fun onFell(listener: (settlement: Int) -> Unit) {
      }
    }
  }
}
