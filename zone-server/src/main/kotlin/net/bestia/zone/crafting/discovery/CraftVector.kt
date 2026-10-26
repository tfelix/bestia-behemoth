package net.bestia.zone.crafting.discovery

import kotlin.math.sqrt

/** A point in the crafting feature space. An axis it does not name is 0. */
data class CraftVector(private val values: Map<CraftAxis, Double>) {

  init {
    values.forEach { (axis, value) ->
      require(value in -1.0..1.0) { "$axis must lie in -1..1, was $value" }
    }
  }

  operator fun get(axis: CraftAxis): Double = values[axis] ?: 0.0

  /** One value per axis in [CraftAxis] order, which is the order the client reads them in. */
  fun toAxisList(): List<Float> = CraftAxis.entries.map { this[it].toFloat() }

  fun distanceTo(other: CraftVector): Double =
    sqrt(CraftAxis.entries.sumOf { axis -> (this[axis] - other[axis]).let { it * it } })

  companion object {
    val ORIGIN = CraftVector(emptyMap())

    /** The amount-weighted mean of [parts]: the proportion of materials is the shape, not their count. */
    fun mixOf(parts: List<Pair<CraftVector, Int>>): CraftVector {
      val total = parts.sumOf { it.second }
      require(total > 0) { "A mix needs at least one unit of material" }

      return CraftVector(CraftAxis.entries.associateWith { axis ->
        parts.sumOf { (vector, amount) -> vector[axis] * amount } / total
      })
    }
  }
}
