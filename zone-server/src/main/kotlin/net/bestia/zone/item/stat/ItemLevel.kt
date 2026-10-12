package net.bestia.zone.item.stat

import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * An item's level from its base level and what it carries. The docs' forging page derives the numbers: a top
 * single stat at item level L is about L/3, and an open socket is worth what a typical essence brings.
 *
 * Base level and stat level add like the sides of a right triangle, so more stats always raise the level, and
 * a strong base absorbs a small bonus instead of adding to it in full.
 */
object ItemLevel {

  const val LEVELS_PER_POINT = 3
  const val POINTS_PER_SOCKET = 5

  fun of(baseLevel: Int, stats: Collection<StatValue>, sockets: Int = 0): Int {
    val statLevel = LEVELS_PER_POINT * pointsOf(stats, sockets)

    return sqrt(baseLevel.toDouble() * baseLevel + statLevel * statLevel).roundToInt()
  }

  /** Never below zero: drawbacks may cancel bonuses, but an item does not fall under its base level. */
  fun pointsOf(stats: Collection<StatValue>, sockets: Int = 0): Double {
    val points = stats.sumOf { it.stat.pointsOf(it.value) } + sockets * POINTS_PER_SOCKET

    return points.coerceAtLeast(0.0)
  }
}
