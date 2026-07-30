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

  /**
   * How much of this latitude's air arrives on an eastward wind rather than a westward one, from 0 to 1.
   *
   * [zonalSign] is a step function, and a step function is wrong in a way that is visible from orbit. The
   * advection sweep runs one row at a time in the direction the wind blows, so where the sign flips between two
   * adjacent rows the two are built from opposite upwind histories - one row's air crossed a continent, the
   * next row's crossed an ocean - and the precipitation field acquires a **discontinuity running the entire
   * width of the map**. It survives the mixing blur, which averages over a few cells and cannot repair a jump
   * that large, and `BiomeStage` then thresholds on it and draws a perfectly straight stripe one climate cell
   * tall across every continent it touches. On a world spanning 68 degrees there are six such stripes.
   *
   * The physics says the same thing the picture does: the boundary between the trades and the westerlies is
   * the subtropical ridge, a broad belt of light and variable wind, not a line where the wind reverses. Air
   * there arrives from both sides. Blending the two sweeps over [BELT_TRANSITION] degrees is what a belt
   * boundary actually looks like, and it is why this returns a share rather than a direction.
   *
   * In degrees of latitude, so it spans the same slice of the planet on a world of any size or resolution.
   */
  fun eastwardShare(latitude: Double, seasonalShift: Double = 0.0): Double {
    val hemisphere = if (latitude >= 0.0) 1.0 else -1.0
    val shifted = abs(latitude) - seasonalShift * hemisphere

    val intoWesterlies = smoothstep(TRADE_LIMIT - BELT_TRANSITION, TRADE_LIMIT + BELT_TRANSITION, shifted)
    val intoPolar = smoothstep(WESTERLY_LIMIT - BELT_TRANSITION, WESTERLY_LIMIT + BELT_TRANSITION, shifted)

    return intoWesterlies * (1.0 - intoPolar)
  }

  private fun smoothstep(edge0: Double, edge1: Double, x: Double): Double {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)
  }

  /** Poleward edge of the trade-wind belt, in degrees. */
  private const val TRADE_LIMIT = 30.0

  /** Poleward edge of the westerly belt, in degrees. */
  private const val WESTERLY_LIMIT = 60.0

  /**
   * Half-width of the belt boundary, in degrees of latitude. See [eastwardShare].
   *
   * Wide enough that the blend spans several rows of the climate grid at any resolution this pipeline runs -
   * four rows on a 128 km world at 4 km cells, fifteen on a 512 km one - because a blend narrower than one
   * row is a step function again. It must stay clear of [seasonalShift][ClimateParams.seasonalShift] plus the
   * gap between the two limits, or the trade and polar transitions start eating each other.
   */
  private const val BELT_TRANSITION = 8.0

  private const val REFERENCE_TEMPERATURE = 20.0
}
