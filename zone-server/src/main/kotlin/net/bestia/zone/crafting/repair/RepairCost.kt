package net.bestia.zone.crafting.repair

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min

/**
 * What a repair costs: restoring a structure's whole health takes half its kit's materials and half its build
 * time. See the design docs' player settlements page.
 *
 * @param materials item id -> amount of the whole kit; empty for a structure no kit makes
 */
data class RepairCost(val maxHp: Int, val buildSeconds: Float, val materials: Map<Long, Int>) {

  /** Health one worker restores per second. */
  val hpPerSecond: Float
    get() {
      return (maxHp / (buildSeconds * SHARE)).toFloat()
    }

  /** Each material needed to restore [hp], rounded up. */
  fun materialsFor(hp: Int): Map<Long, Int> {
    return materials.mapValues { (_, amount) -> ceil(amount * SHARE * hp / maxHp).toInt() }
  }

  /**
   * How much of [wantedHp] the materials in [held] pay for, and what that takes. A material that runs short
   * limits the rest, so nothing is taken that cannot be used.
   */
  fun deposit(wantedHp: Int, held: Map<Long, Int>): Deposit {
    if (materials.isEmpty()) return Deposit(wantedHp, emptyMap())

    val wanted = materialsFor(wantedHp)
    val share = wanted.minOf { (itemId, amount) -> min(1.0, held.getOrDefault(itemId, 0).toDouble() / amount) }
    val coveredHp = floor(wantedHp * share).toInt()
    if (coveredHp == 0) return Deposit(0, emptyMap())

    return Deposit(coveredHp, materialsFor(coveredHp).filterValues { it > 0 })
  }

  data class Deposit(val hp: Int, val taken: Map<Long, Int>)

  private companion object {
    const val SHARE = 0.5
  }
}
