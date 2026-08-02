package net.bestia.worldgen.voxel

import net.bestia.worldgen.fields.Noise
import net.bestia.worldgen.karst.CaveChannels
import net.bestia.worldgen.karst.CaveParams
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.MarkerFeature
import net.bestia.worldgen.vector.PointMarker
import net.bestia.worldgen.vector.Quantize
import net.bestia.worldgen.vector.Vec2d
import net.bestia.worldgen.vector.VectorFeature
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The caves reaching one chunk, as spans of rock to take away.
 *
 * The chunk-tier half of `karst/CaveStage`, and the counterpart of [OreVeins]: the world tier holds a few
 * hundred polylines, chunk generation asks the index which of them reach this chunk - almost always none -
 * and the passage materialises from the feature's own stored attributes. Nothing anywhere holds a per-voxel
 * cave field.
 *
 * ### Why two chunks agree
 *
 * The river seam theorem, verbatim. A passage is **one continuous polyline** with its floor, height and
 * half-width stored per station, so a column on either side of a chunk border projects onto the same curve
 * and reads the same three numbers out of the same table. Nothing here is chunk-seeded and nothing is
 * cached, so there is no state for two chunks to disagree about. The one branch that could have gone two ways
 * - is this column inside the passage - goes through [Quantize], exactly as the doorway test in
 * `TownStructures` does.
 *
 * ### Three guards on the roof, and only one of them is sufficient
 *
 * 1. **The stage clamps against the kilometre raster.** Necessary and nowhere near sufficient: the chunk sees
 *    `base.heightAt` plus every vector feature stamped on it, which can be tens of metres below the coarse
 *    cell a river channel or a glacial trough was cut into.
 * 2. **This clamps against the column's actual surface**, which is already in frame as `top`. If that pulls
 *    the roof below the floor the span is simply dropped and the passage pinches out - the honest outcome,
 *    and much better than a slot appearing in a valley floor.
 * 3. **An entrance is the single authorised exception**, and is allowed to break the surface because that is
 *    what a cave mouth is. It is vetoed by anything built over the column and, at the call site, by standing
 *    water.
 *
 * ### The walls are two smooth fields, not a hash
 *
 * `SurfaceSampler.biomeAt` measured this lesson and paid for it: a per-position hash at a metre per voxel
 * reads as display noise rather than as texture, because each voxel's decision is independent of its
 * neighbours'. So the plan half-width and the section height are each perturbed by a **smooth 2D fbm**, at
 * wavelengths of a few tens of metres - which gives a passage that pinches and opens out along its length the
 * way a real one does, and keeps neighbouring columns agreeing about where the wall is.
 */
class CaveNetwork(
  features: List<VectorFeature>,
  seed: Long,
  /**
   * The stage's own tuning, not a copy of the two numbers this needs.
   *
   * Same argument `Stratigraphy.of` makes: the roof cover a passage was *placed* with and the roof cover it is
   * *cut* with have to be the same number, and two constants in sibling packages agree only until one moves.
   */
  private val params: CaveParams
) {

  private class Passage(
    val feature: MarkerFeature,
    val floorChannel: Int,
    val heightChannel: Int,
    val halfWidthChannel: Int
  )

  private class Mouth(val at: Vec2d, val radius: Double)

  /**
   * How far outside its own bounding box a passage can reach.
   *
   * A [MarkerFeature] reports `corridorWidthMax` of zero - it is geometry, and geometry has no width - so its
   * bounds are the centerline's and a column half a passage-width outside them would be skipped. Without the
   * expansion a passage has a notch cut out of it at every extremum, which is the same defect
   * `TownStructures.MAX_WALL_HALF_THICKNESS` exists to prevent.
   */
  private val reach = params.maxHalfWidth * WALL_NOISE_HEADROOM

  private val passages: List<Passage> = features
    .asSequence()
    .filter { it.kind == FeatureKind.CAVE_PASSAGE }
    .filterIsInstance<MarkerFeature>()
    .mapNotNull { feature ->
      runCatching {
        Passage(
          feature = feature,
          floorChannel = feature.channel(CaveChannels.FLOOR),
          heightChannel = feature.channel(CaveChannels.HEIGHT),
          halfWidthChannel = feature.channel(CaveChannels.HALF_WIDTH)
        )
      }.getOrNull()
    }
    .toList()

  private val mouths: List<Mouth> = features
    .asSequence()
    .filter { it.kind == FeatureKind.CAVE_ENTRANCE }
    .filterIsInstance<PointMarker>()
    .mapNotNull { marker ->
      runCatching { Mouth(marker.position, marker.attribute(CaveChannels.MOUTH)) }.getOrNull()
    }
    .toList()

  private val planSeed = seed xor PLAN_SALT
  private val sectionSeed = seed xor SECTION_SALT

  val isEmpty get() = passages.isEmpty()

  /**
   * Adds a removal span for every passage crossing this column.
   *
   * @param top the column's own terrain height, after every vector feature - the surface guard
   * @param builtOver the highest point anything standing on this column reaches, or NaN. An entrance under a
   *   building is not a way in, it is a hole in somebody's floor.
   */
  fun columnAt(worldX: Double, worldY: Double, top: Double, builtOver: Double, into: StructureSpans) {
    if (passages.isEmpty()) return

    val at = Vec2d(worldX, worldY)
    val open = builtOver.isNaN() && isAtAMouth(worldX, worldY)

    for (passage in passages) {
      if (!passage.feature.centerline.bbox.expanded(reach).contains(worldX, worldY)) continue

      val projection = passage.feature.centerline.project(at)
      if (projection.beyondEnd) continue

      val stations = passage.feature.stations ?: continue
      val halfWidth = stations.sample(passage.halfWidthChannel, projection.u) * planNoiseAt(worldX, worldY)
      if (Quantize.isAbove(projection.distance, halfWidth)) continue

      val floor = stations.sample(passage.floorChannel, projection.u)
      val height = stations.sample(passage.heightChannel, projection.u) * sectionNoiseAt(worldX, worldY)

      // An elliptical arch rather than a rectangular box. `t` is how far across the passage this column is, so
      // the roof comes down to meet the floor at the walls - which is what stops a gallery reading as a mine
      // adit, and costs one square root.
      val t = if (halfWidth <= 0.0) 1.0 else (projection.distance / halfWidth).coerceIn(0.0, 1.0)
      val arch = sqrt((1.0 - t * t).coerceAtLeast(0.0))

      val raw = floor + height * arch
      // Guard 2, and guard 3 as its exception. A mouth may reach above the ground - the void has to clear the
      // surface voxel or the carve leaves a lid over it, exactly as a mine shaft does.
      val roof = if (open) raw + MOUTH_HEADROOM else min(raw, top - params.minRoofCover)

      into.remove(floor, roof)
    }
  }

  private fun isAtAMouth(worldX: Double, worldY: Double): Boolean {
    for (mouth in mouths) {
      val dx = worldX - mouth.at.x
      val dy = worldY - mouth.at.y
      if (dx * dx + dy * dy <= mouth.radius * mouth.radius * MOUTH_FLARE * MOUTH_FLARE) return true
    }
    return false
  }

  /** Widens and pinches the passage in plan, so a gallery is not a constant-width tube. */
  private fun planNoiseAt(worldX: Double, worldY: Double): Double =
    1.0 + Noise.fbm(planSeed, worldX / PLAN_WAVELENGTH, worldY / PLAN_WAVELENGTH, NOISE_OCTAVES) * PLAN_AMPLITUDE

  /** Raises and lowers the roof. A separate field from the plan one, or every wide place is also a tall one. */
  private fun sectionNoiseAt(worldX: Double, worldY: Double): Double =
    1.0 + Noise.fbm(
      sectionSeed, worldX / SECTION_WAVELENGTH, worldY / SECTION_WAVELENGTH, NOISE_OCTAVES
    ) * SECTION_AMPLITUDE

  private companion object {
    /**
     * How much wider than its nominal half-width the two wall fields can make a passage.
     *
     * Only used to expand a bounding box, and deliberately generous: an under-estimate here is a column that
     * should have been inside the passage and was never tested, which reads as a notch in the wall rather than
     * as a number being wrong.
     */
    const val WALL_NOISE_HEADROOM = 1.6

    const val PLAN_SALT = 0x4CA7E0DE5C0FFEEL
    const val SECTION_SALT = 0x1EC7A11B0B0DEADL

    /**
     * Wavelengths of the two wall fields, in metres.
     *
     * Tens of metres rather than the metre a voxel is: at a voxel the fields would be noise, and at hundreds
     * they would be invisible inside one chamber. Different from each other and not a ratio of small integers,
     * so a wide place and a tall place do not line up into a repeating bead pattern along the passage.
     */
    const val PLAN_WAVELENGTH = 47.0
    const val SECTION_WAVELENGTH = 31.0

    /** How much of its nominal size each field can add or take away. Under [CaveStage.WALL_NOISE_HEADROOM]. */
    const val PLAN_AMPLITUDE = 0.45
    const val SECTION_AMPLITUDE = 0.35

    const val NOISE_OCTAVES = 2

    /**
     * Metres a mouth's void reaches above the ground.
     *
     * Must exceed one voxel, for the reason `TownStructures.MineHead.SHAFT_HEADROOM` records: a void whose
     * ceiling stops at the surface leaves the voxel the surface falls inside, and that voxel is a lid.
     */
    const val MOUTH_HEADROOM = 1.5

    /**
     * How much wider than its recorded radius a mouth's exemption reaches.
     *
     * The mouth radius is the passage's half-width where it broke out, and the opening wants to be a little
     * wider than the passage behind it - a cave entrance is a notch in a hillside, not a pipe end.
     */
    const val MOUTH_FLARE = 2.2
  }
}
