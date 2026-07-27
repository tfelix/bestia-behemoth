package net.bestia.worldgen.climate

import net.bestia.worldgen.vector.Vec2d
import kotlin.math.abs
import kotlin.math.sign

/**
 * Prevailing wind direction by latitude: the Hadley, Ferrel and Polar cells.
 *
 * Three bands per hemisphere, each with a fixed zonal direction, deflected meridionally by Coriolis.
 * This is a crude model of a phenomenon that takes a supercomputer to do properly, and it is entirely
 * sufficient, because everything downstream only needs the *direction air arrives from*. What that
 * buys is rain shadows on the correct side of every mountain range on the map, and deserts that sit
 * behind ranges rather than wherever the noise happened to be dry - which is the single largest
 * believability win in the climate model.
 */
object Winds {

  /**
   * Unit direction the wind is travelling towards at [latitude] degrees, in world axes: `+x` is east
   * and `+y` is north.
   *
   * @param seasonalShift degrees to move the band boundaries poleward. The intertropical convergence
   *   zone migrates with the sun, and shifting the bands with it is what produces a monsoon: the same
   *   coast is upwind of the ocean in one season and downwind of a continent in the other.
   */
  fun directionAt(latitude: Double, seasonalShift: Double = 0.0): Vec2d {
    val hemisphere = if (latitude >= 0.0) 1.0 else -1.0
    val shifted = abs(latitude) - seasonalShift * hemisphere

    // Zonal direction first: trades blow east to west, the westerlies blow west to east, and the
    // polar cell reverses again.
    val (zonal, meridional) = when {
      shifted < TRADE_LIMIT -> -1.0 to -0.35
      shifted < WESTERLY_LIMIT -> 1.0 to 0.30
      else -> -1.0 to 0.20
    }

    // Coriolis deflects to the right in the northern hemisphere and to the left in the southern, which
    // is what turns a purely meridional pressure gradient into the observed diagonal flow.
    return Vec2d(zonal, meridional * hemisphere).normalized()
  }

  /**
   * How much water a parcel of air at this temperature can hold, relative to 20 degrees.
   *
   * A doubling per ten degrees - the shape of the Clausius-Clapeyron relation, not its constants.
   * Getting the shape right is what matters here: it is why cold air cannot rain much however far it
   * travels over water, and therefore why high latitudes are dry.
   */
  fun capacity(temperature: Double): Double =
    Math.pow(2.0, (temperature - REFERENCE_TEMPERATURE) / 10.0).coerceIn(0.04, 4.0)

  /** Which way the sweep runs along a row: +1 eastward, -1 westward. */
  fun zonalSign(latitude: Double, seasonalShift: Double = 0.0): Int =
    sign(directionAt(latitude, seasonalShift).x).toInt().let { if (it == 0) 1 else it }

  /** Poleward edge of the trade-wind belt, in degrees. */
  private const val TRADE_LIMIT = 30.0

  /** Poleward edge of the westerly belt, in degrees. */
  private const val WESTERLY_LIMIT = 60.0

  private const val REFERENCE_TEMPERATURE = 20.0
}
