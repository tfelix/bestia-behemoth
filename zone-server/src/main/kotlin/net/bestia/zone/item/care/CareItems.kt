package net.bestia.zone.item.care

/**
 * Which items a bestia may use to look after itself, beyond the common food and bandages [BagCare] allows: those in
 * [alsoUse] on top, and never those in [neverUse], even where they would qualify.
 */
data class CareItems(
  val neverUse: List<Long> = emptyList(),
  val alsoUse: List<Long> = emptyList(),
) {

  val isValid: Boolean
    get() {
      return neverUse.size <= MAX_ITEMS && alsoUse.size <= MAX_ITEMS
    }

  companion object {
    const val MAX_ITEMS = 8
  }
}
