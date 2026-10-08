package net.bestia.zone.item.material

import net.bestia.zone.battle.Element

/**
 * How much damage a stack of [material] takes on the ground before it is gone.
 *
 * [hardness] comes off every hit, so blows too light to matter never add up to anything.
 */
data class ItemMaterialSpec(
  val material: ItemMaterial,
  val integrity: Int,
  val hardness: Int = 0,
  /** Percent of a hit that lands, per attack element. An element that is not listed lands in full. */
  val elementMultipliers: Map<Element, Int> = emptyMap(),
) {

  fun damageFrom(amount: Int, element: Element): Int {
    val weighted = amount * multiplierPercent(element) / 100

    return (weighted - hardness).coerceAtLeast(0)
  }

  fun multiplierPercent(element: Element): Int {
    return elementMultipliers[element] ?: FULL_PERCENT
  }

  private companion object {
    const val FULL_PERCENT = 100
  }
}
