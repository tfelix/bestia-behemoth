package net.bestia.zone.weather

import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.world.mana.ManaConcentrationService
import org.springframework.stereotype.Component
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin

/**
 * The mana half of Weather Sense: the field where the player stands from level 1, and from level 2 the nearest
 * clearly stronger and weaker field within 1 km, 2 km at level 3.
 */
@Component
class ManaSense(
  private val mana: ManaConcentrationService,
) {

  /** Null below level 1. [at] in voxels, which are metres. */
  fun read(at: Vec3L, weatherSenseLevel: Int): ManaReading? {
    if (weatherSenseLevel <= 0) return null
    val here = mana.manaAt(at)
    val rangeMetres = RANGE_METRES_BY_LEVEL.getOrElse(weatherSenseLevel - 1) { RANGE_METRES_BY_LEVEL.last() }
    if (rangeMetres == 0) return ManaReading(here, null, null)

    return ManaReading(
      here = here,
      stronger = nearest(at, rangeMetres) { it >= here + CLEAR_DIFFERENCE },
      weaker = nearest(at, rangeMetres) { it <= here - CLEAR_DIFFERENCE },
    )
  }

  /** Walks rings outward, so the first match is the nearest one at this sampling. */
  private fun nearest(at: Vec3L, rangeMetres: Int, matches: (Double) -> Boolean): ManaPointer? {
    for (distance in RING_STEP_METRES..rangeMetres step RING_STEP_METRES) {
      for (step in 0 until DIRECTIONS) {
        val direction = 2 * PI * step / DIRECTIONS
        val sample = Vec3L(
          at.x + (cos(direction) * distance).roundToLong(),
          at.y + (sin(direction) * distance).roundToLong(),
          at.z
        )
        if (matches(mana.manaAt(sample))) return ManaPointer(direction, distance.toDouble())
      }
    }

    return null
  }

  private companion object {
    /** Level 1 reads only the spot; levels 2 and 3 search 1 and 2 km. */
    val RANGE_METRES_BY_LEVEL = listOf(0, 1000, 2000)

    const val RING_STEP_METRES = 250
    const val DIRECTIONS = 16

    /** A field a tenth stronger or weaker: the mana field drifts too slowly for a smaller step to mean much. */
    const val CLEAR_DIFFERENCE = 0.1
  }
}

/** The mana field where the player stands, 0 to 1, and where it grows clearly stronger or weaker, if in range. */
data class ManaReading(val here: Double, val stronger: ManaPointer?, val weaker: ManaPointer?)

/** [directionRadians] like the wind's: 0 east, counter-clockwise. */
data class ManaPointer(val directionRadians: Double, val distanceMetres: Double)
