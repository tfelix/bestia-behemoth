package net.bestia.worldgen.voxel

import net.bestia.worldgen.civ.BuildingChannels
import net.bestia.worldgen.civ.BuildingFunction
import net.bestia.worldgen.civ.RoofMaterial
import net.bestia.worldgen.civ.RoofShape
import net.bestia.worldgen.civ.WallMaterial
import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.FootprintFeature
import net.bestia.worldgen.vector.Quantize
import net.bestia.worldgen.vector.VectorFeature
import kotlin.math.atan2

/**
 * The buildings a town laid out, as props: one `FeatureKind.BUILDING` footprint in, one prop out.
 *
 * ### Not a scatter, for [PoiProps]' reason
 *
 * The world tier has already decided that there is a shop here, how big it is and which way it faces. Nothing
 * here re-decides any of that - no biome test, no water test, no cap veto. `TownStage` graded the lot and
 * `TownBuildings` sized the building on it, and a second opinion taken at metre scale could only disagree with
 * a decision made with more information.
 *
 * ### The one prop source that takes no [PropSite] at all
 *
 * Every other source resolves its ground through `PropSite`, because a tree has no opinion about what
 * elevation it grows at. A building does: `BuildingChannels.FLOOR_ELEVATION` is the level the lot was graded
 * to, taken from the *front* of the plot so a row of houses on a slope steps with the street. Sampling the
 * column instead would put each building at whatever height its own centre happened to end up, which is the
 * jog between neighbours the front-of-plot rule exists to remove.
 *
 * It is not consulted as a veto either, and that is not an oversight - **`ChunkMaterializer.trunkSite` refuses
 * exactly the ground a building stands on.** Its whole job is keeping scattered props out of streets, off
 * bridges and from under roofs, and the roof it checks for is this building's own: `structures.columnAt` writes
 * the floor slab, `builtOver` comes back non-`NaN`, and the site answers "nothing may stand here". Asking it
 * would veto every building in the world, which is precisely what the first version of this file did.
 *
 * There is nothing left for a veto to catch. A tree needs one because a scatter is a statement about a *field*
 * and the field cannot see a mine shaft; a building was placed by `TownStage`, which laid out the lot, graded
 * it, and kept it clear of everything a veto would look for.
 *
 * ### Fortifications are not here
 *
 * `BuildingFunction.FORTIFICATION` stays voxels. A keep and a wall tower are part of a wall circuit, and a
 * circuit that an agent can walk through is not a wall. See `TownStructures.Structure.voxelised` - the two
 * conditions are deliberately written as one predicate in one place, because a building that is both would be
 * drawn twice and a building that is neither would vanish.
 */
class BuildingProps(
  private val config: WorldConfig,
  features: List<VectorFeature>
) {

  /** One building: where it is, how big, which way round, and what it is made of. */
  private class Site(
    val x: Double,
    val y: Double,
    val floor: Double,
    val halfLength: Double,
    val halfWidth: Double,
    val eaveHeight: Double,
    val yaw: Double,
    val function: BuildingFunction,
    val flags: Int
  )

  private val cellUnits = Quantize.toFixed(BUILDING_CELL_SIZE)

  private val sites: List<Site> = features
    .asSequence()
    .filter { it.kind == FeatureKind.BUILDING }
    .filterIsInstance<FootprintFeature>()
    .mapNotNull { footprint ->
      // `runCatching` for `TownStructures`' reason: a channel this reads may be missing on a hand-built
      // footprint in a test, and dropping that one building is better than throwing a whole chunk away.
      runCatching {
        val function = BuildingFunction.entries[
          footprint.attribute(BuildingChannels.FUNCTION).toInt()
        ]
        if (function == BuildingFunction.FORTIFICATION) return@runCatching null

        val floor = footprint.attribute(BuildingChannels.FLOOR_ELEVATION)
        val storeys = footprint.attribute(BuildingChannels.STOREYS).toInt().coerceIn(1, MAX_STOREYS)

        Site(
          x = footprint.center.x,
          y = footprint.center.y,
          floor = floor,
          halfLength = footprint.halfLength,
          halfWidth = footprint.halfWidth,
          eaveHeight = storeys * STOREY_HEIGHT,
          yaw = atan2(footprint.bearing.y, footprint.bearing.x),
          function = function,
          flags = PropFlags.building(
            roofShape = ordinalIn(footprint, BuildingChannels.ROOF_SHAPE, RoofShape.entries.size),
            wallMaterial = ordinalIn(footprint, BuildingChannels.WALL_MATERIAL, WallMaterial.entries.size),
            roofMaterial = ordinalIn(footprint, BuildingChannels.ROOF_MATERIAL, RoofMaterial.entries.size)
          )
        )
      }.getOrNull()
    }
    .toList()

  val isEmpty get() = sites.isEmpty()

  /**
   * The buildings whose own centre falls inside one chunk, as props.
   *
   * Ownership is the centre's voxel column in integers, for [VegetationScatter.propsIn]'s reason: a bounds
   * test on a closed interval hands an object exactly on a chunk boundary to both of the chunks that share it.
   * A building is metres wide and routinely straddles a chunk border, so this is the only test that gives one
   * answer.
   */
  fun propsIn(chunk: ChunkPos, into: PropInstances) {
    if (sites.isEmpty()) return

    for (building in sites) {
      if (config.chunkOf(building.x) != chunk.x) continue
      if (config.chunkOf(building.y) != chunk.y) continue

      into.add(
        kind = PropKind.BUILDING,
        identity = PropId.of(PropKind.BUILDING, cellOf(building.x), cellOf(building.y)),
        x = building.x,
        y = building.y,
        ground = building.floor,
        heightM = building.eaveHeight,
        radiusM = building.halfLength,
        halfWidthM = building.halfWidth,
        yawRad = building.yaw,
        flags = building.flags,
        subKind = building.function.ordinal
      )
    }
  }

  /**
   * One channel's ordinal, clamped into its enum's range.
   *
   * Clamped rather than checked, which is the opposite of what [PropFlags.building] does one line later, and
   * the two are consistent: an out-of-range value *here* is a category read off a `Double` channel that was
   * never interpolated, so the honest recovery is the same one `TownStructures` makes for a shrine's faction -
   * draw the plain version rather than throw a chunk away. An out-of-range value *there* means an enum grew
   * and the packing did not, which is a bug in this file and should be loud.
   */
  private fun ordinalIn(footprint: FootprintFeature, channel: String, size: Int): Int =
    footprint.attribute(channel).toInt().coerceIn(0, size - 1)

  /**
   * The lattice cell that names this building, for [PropId].
   *
   * A metre, exactly as [PoiProps.cellOf] takes one, and for the same argument: this lattice spaces nothing,
   * it only names, so it can take the finest quantisation the fixed-point grid offers and never need to move.
   * Injective because two buildings cannot share a centre - lots do not overlap, which `TownStage`'s own
   * plot-overlap test guarantees.
   */
  private fun cellOf(world: Double): Long = Math.floorDiv(Quantize.toFixed(world), cellUnits)

  companion object {

    /** See [cellOf]. Not a spacing: nothing is laid out on this lattice, it only names things. */
    const val BUILDING_CELL_SIZE = 1.0

    /**
     * Mirrors `Building.STOREY_HEIGHT` and `TownBuildings`' own cap.
     *
     * Duplicated rather than imported because `civ` is a stage package and `voxel` is the chunk tier; the
     * feature carries the storey *count*, and the height a storey stands for is a fact about how a building is
     * drawn rather than about how a town is laid out.
     */
    const val STOREY_HEIGHT = 2.6
    const val MAX_STOREYS = 8
  }
}
