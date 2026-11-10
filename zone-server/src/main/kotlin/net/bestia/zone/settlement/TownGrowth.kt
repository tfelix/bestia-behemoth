package net.bestia.zone.settlement

import net.bestia.zone.entity.StaticEntityKind
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * How a player settlement grows with what is built in it: its building points, the sphere they span and the
 * sign they harden. Numbers from the design docs' player settlements page.
 */
object TownGrowth {

  const val BASE_RADIUS_METRES = 150.0
  const val RADIUS_PER_POINT_METRES = 25.0
  const val MAX_RADIUS_METRES = 500.0

  const val BASE_SIGN_HP = 2_000
  const val SIGN_HP_PER_POINT = 500
  const val MAX_SIGN_HP = 30_000

  const val BASE_SIGN_DEFENSE = 10
  const val SIGN_DEFENSE_PER_POINT = 2
  const val MAX_SIGN_DEFENSE = 60

  /** The buildings that only work inside a town, and keep its founder from tearing the sign down. */
  val FUNCTIONAL_KINDS: Set<StaticEntityKind> = setOf(StaticEntityKind.AUCTION_HOUSE)

  /** A finished structure's worth to the town it stands in. */
  fun pointsOf(kind: StaticEntityKind): Double {
    return when (kind) {
      in FUNCTIONAL_KINDS -> FUNCTIONAL_BUILDING_POINTS
      StaticEntityKind.WORKBENCH, StaticEntityKind.FURNACE, StaticEntityKind.FORGE -> 1.0
      StaticEntityKind.STRUCTURE_WALL, StaticEntityKind.STRUCTURE_FLOOR, StaticEntityKind.STRUCTURE_STAIRS -> 0.1
      else -> 0.0
    }
  }

  fun radiusMetres(points: Double): Double {
    return min(MAX_RADIUS_METRES, BASE_RADIUS_METRES + RADIUS_PER_POINT_METRES * points)
  }

  /** A town's size: the points of every building inside its sphere, and the sphere they span. */
  data class Size(val points: Double, val radiusMetres: Double)

  /**
   * A building on the edge pushes the edge out, which can bring the next one in, so this repeats until nothing
   * new joins.
   */
  fun sizeOf(buildings: List<Building>): Size {
    var radius = BASE_RADIUS_METRES
    while (true) {
      val points = buildings.filter { it.distanceMetres <= radius }.sumOf { pointsOf(it.kind) }
      val grown = radiusMetres(points)
      if (grown <= radius) return Size(points, radius)
      radius = grown
    }
  }

  /** A finished structure near a sign. */
  data class Building(val kind: StaticEntityKind, val distanceMetres: Double)

  fun signMaxHp(points: Double): Int {
    return min(MAX_SIGN_HP, BASE_SIGN_HP + (SIGN_HP_PER_POINT * points).roundToInt())
  }

  fun signDefense(points: Double): Int {
    return min(MAX_SIGN_DEFENSE, BASE_SIGN_DEFENSE + (SIGN_DEFENSE_PER_POINT * points).roundToInt())
  }

  private const val FUNCTIONAL_BUILDING_POINTS = 5.0
}
