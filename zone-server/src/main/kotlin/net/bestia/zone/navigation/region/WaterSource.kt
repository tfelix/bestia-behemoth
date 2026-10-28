package net.bestia.zone.navigation.region

import net.bestia.worldgen.vector.Aabb
import net.bestia.worldgen.vector.Vec2d

/**
 * Where the world is too deep to walk, asked of the generated world rather than of loaded chunks.
 *
 * A seam of the same shape as `NavWorldSource`: it keeps [RegionGrid] away from the world tier's samplers, and
 * it lets a test draw a lake with a lambda instead of generating terrain.
 *
 * Unlike the chunk tier this does not see what players changed. A pond dug into a field is not in it; a route
 * the grid lays past one is walked round by the local tier, which reads the real ground.
 */
interface WaterSource {

  /** False until the world is loaded; the grid then knows nothing and lays every leg straight. */
  val isReady: Boolean

  /**
   * The water over [area], to be sampled within it. Safe to call off the tick thread.
   *
   * @param exact whether the ground is measured with every feature in it. Without, what is narrower than a
   *   grid cell - a road, a street, a building - is left out: it cannot turn a cell's majority, and it is most
   *   of what a sample costs.
   */
  fun survey(area: Aabb, exact: Boolean): WaterSurvey

  companion object {
    /** No world at all, for tests about something other than water. */
    val NONE = object : WaterSource {
      override val isReady get() = false
      override fun survey(area: Aabb, exact: Boolean) = WaterSurvey({ _, _ -> false }, emptyList())
    }
  }
}

/**
 * One area's water, and the lie of the ground under it.
 *
 * @property isDeep whether a column, in world metres, stands under more water or lava than anyone can wade
 * @property crossings the decks that span it, each from one bank to the other: they are a few metres wide and
 *   a coarse sample of [isDeep] would step over them, so they are handed over as lines
 * @property groundAt the height of the ground at a column, in metres. Asked for a column just before [isDeep]
 *   is asked for the same one, which lets a source measure the ground once for both.
 * @property roads the centre lines of the roads and streets through it, which are the easy going
 */
class WaterSurvey(
  val isDeep: (worldX: Double, worldY: Double) -> Boolean,
  val crossings: List<Pair<Vec2d, Vec2d>>,
  val groundAt: (worldX: Double, worldY: Double) -> Double = { _, _ -> 0.0 },
  val roads: List<List<Vec2d>> = emptyList(),
)
