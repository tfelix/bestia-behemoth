package net.bestia.zone.ai.profile

import net.bestia.zone.prop.GatherCategory

/**
 * What a gatherer takes: nothing of a category in [skip], and with [onlyItems] only a prop that yields one of them.
 * Empty, it takes everything.
 */
data class GatherFilter(
  val skip: Set<GatherCategory> = emptySet(),
  val onlyItems: List<Long> = emptyList(),
) {

  val isValid: Boolean
    get() {
      return onlyItems.size <= MAX_ITEMS
    }

  fun allows(category: GatherCategory, yields: Collection<Long>): Boolean {
    return category !in skip && (onlyItems.isEmpty() || yields.any { it in onlyItems })
  }

  companion object {
    const val MAX_ITEMS = 8
  }
}
