package net.bestia.worldgen.mana

import kotlin.math.pow

/**
 * How much mana is in the air when a world begins, from the worldgen mana potential.
 *
 * The potential is a rank, spread evenly over the land, so read as an amount it made a quarter of every world
 * "high". The curve keeps most land low and lets only the top of the rank, the land around the Wounds, start
 * strong. What is left above it is the room that play and the Wounds fill.
 *
 * Here rather than in the zone because the weather regions summarise the density too, so a fresh world's
 * storms and the zone's mana field read the same curve.
 */
object StartDensity {

  private const val FLOOR = 0.10
  private const val SPAN = 0.55
  private const val STEEPNESS = 4.0

  fun of(potential: Double): Double = FLOOR + SPAN * potential.coerceIn(0.0, 1.0).pow(STEEPNESS)
}
