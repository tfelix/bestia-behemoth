package net.bestia.worldgen.civ

import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.history.HistoryChannels
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.vector.BlendMode
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.FootprintFeature
import net.bestia.worldgen.vector.PointMarker
import net.bestia.worldgen.voxel.BlockType
import net.bestia.worldgen.voxel.PropKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Town layout and the blocks it turns into, against a real world. */
class TownStageTest {

  private val generated: GeneratedWorld by lazy {
    StandardWorld.build(StandardWorld.demoConfig(seed = 909L).copy(widthCells = 160, heightCells = 160))
  }

  private val buildings: List<FootprintFeature> by lazy {
    generated.world.features.all()
      .filter { it.kind == FeatureKind.BUILDING }
      .filterIsInstance<FootprintFeature>()
  }

  @Test
  fun `standing settlements get buildings and ruins do not`() {
    val history = generated.world.features.all()
      .filter { it.kind == FeatureKind.SETTLEMENT_HISTORY }
      .filterIsInstance<PointMarker>()
      .associateBy { it.attribute(HistoryChannels.INDEX).toInt() }

    val bySettlement = buildings.groupBy { it.attribute(BuildingChannels.SETTLEMENT).toInt() }
    assertTrue(bySettlement.isNotEmpty(), "no settlement in the world was laid out")

    for ((index, theirs) in bySettlement) {
      val past = history.getValue(index)
      assertTrue(
        past.attribute(HistoryChannels.ABANDONED_YEAR).toInt() == 0,
        "settlement $index is a ruin and has ${theirs.size} buildings in it"
      )
    }
  }

  /**
   * Building counts track population rather than being a constant per settlement.
   *
   * The coupling that makes history visible in the ground: a place history grew is physically bigger than one
   * it did not. Asserted as a correlation rather than a formula, because the cap and the plot supply both bite
   * and neither is a bug.
   */
  @Test
  fun `bigger settlements have more buildings`() {
    val history = generated.world.features.all()
      .filter { it.kind == FeatureKind.SETTLEMENT_HISTORY }
      .filterIsInstance<PointMarker>()
      .associateBy { it.attribute(HistoryChannels.INDEX).toInt() }

    val counted = buildings.groupingBy { it.attribute(BuildingChannels.SETTLEMENT).toInt() }.eachCount()
    val ranked = counted.entries
      .map { history.getValue(it.key).attribute(HistoryChannels.POPULATION) to it.value }
      .sortedBy { it.first }

    assertTrue(ranked.size >= 4, "only ${ranked.size} settlements were laid out")
    assertTrue(
      ranked.last().second > ranked.first().second,
      "the largest settlement (${ranked.last().first.toInt()} people, ${ranked.last().second} buildings) " +
          "has no more buildings than the smallest (${ranked.first().first.toInt()} people, " +
          "${ranked.first().second} buildings)"
    )
  }

  @Test
  fun `every building carries what the materialiser needs`() {
    for (building in buildings) {
      val storeys = building.attribute(BuildingChannels.STOREYS).toInt()
      assertTrue(storeys in 1..8, "building ${building.id} has $storeys storeys")

      val function = building.attribute(BuildingChannels.FUNCTION).toInt()
      assertTrue(function in BuildingFunction.entries.indices, "building ${building.id} has function $function")

      // Ordinals now, where these used to be block ids - a building is a prop rather than a shell of voxels,
      // so its walls are an attribute. An out-of-range value here is a channel read that landed on the wrong
      // number, which shows up as a town built of nothing.
      val wall = building.attribute(BuildingChannels.WALL_MATERIAL).toInt()
      val roof = building.attribute(BuildingChannels.ROOF_MATERIAL).toInt()
      assertTrue(wall in WallMaterial.entries.indices, "building ${building.id} has wall material $wall")
      assertTrue(roof in RoofMaterial.entries.indices, "building ${building.id} has roof material $roof")

      assertTrue(
        building.attribute(BuildingChannels.FLOOR_ELEVATION).isFinite(),
        "building ${building.id} has no floor"
      )
    }
  }

  /**
   * The guard that keeps the next test from passing for the wrong reason.
   *
   * `the ground under a building is level` is only a hard test on a world where something *other than the
   * elevation raster* decides the ground under a town. Its one real failure was a moraine - an `ADD` blend
   * feature that `GlacialStage` deliberately does not rasterise, so `WorldGround`'s base could not see it and
   * `standsLevel` approved a lot with ten metres of ridge under it. On a world where every settlement happens
   * to stand on plain rasterised terrain the test below passes without exercising any of that.
   *
   * So: assert the world still has the ingredient. Seed 909 is not pinned for its scenery.
   */
  @Test
  fun `a building on this world stands on ground the raster does not have`() {
    val additive = generated.world.features.all().filter { it.affectsHeight && it.blend == BlendMode.ADD }
    assertTrue(additive.isNotEmpty(), "seed 909 has no additive landform at all")

    val standing = buildings.count { building ->
      additive.any { it.bbox.contains(building.center.x, building.center.y) }
    }
    assertTrue(
      standing > 0,
      "no building on this world stands on an additive landform, so `the ground under a building is level` " +
          "can no longer fail the way it once did - re-pin the seed to one that has a town on a moraine, " +
          "an alluvial fan or a delta"
    )
  }

  /**
   * A building's pad levels the ground it covers, as far as a pad is allowed to.
   *
   * The property that stops a house being half-buried on a slope, and the reason a building is one feature
   * rather than a marker beside a pad. Checked against the *column source* - the thing chunk generation
   * actually samples - rather than against the pad's own profile, which would be checking the feature against
   * itself.
   *
   * ### Two assertions, because "level" is not what the code promises
   *
   * The pad is capped: `TownStage.PAD_MAX_CUT` and `PAD_MAX_FILL` stop it from carving a cliff or standing a
   * house on a four-metre plinth of invented ground, so on steep enough ground it legitimately falls short.
   * This test asserted a flat one-metre tolerance for every building and passed only because no building in
   * its sample had ever landed on ground that needed more - a reseed of the town stage was enough to find one
   * 2.75 m below its own floor, which is inside what the pad is allowed to leave.
   *
   * So the hard bound is the pad's own limit, which still catches the failure worth catching: real terrain
   * moves tens of metres across a lot, so a pad that stopped running would miss by an order of magnitude
   * rather than by a metre. The one-metre claim survives as a *majority* - if the pad quietly became a
   * best-effort skirt, most buildings would stop being level and this would say so.
   */
  @Test
  fun `the ground under a building is level`() {
    val sample = buildings.sortedBy { it.id.value }.take(24)
    assertTrue(sample.isNotEmpty(), "no buildings to check")

    var samples = 0
    var level = 0

    for (building in sample) {
      val floor = building.attribute(BuildingChannels.FLOOR_ELEVATION)
      val corners = building.corners()

      // Two thirds of the way in from each corner, so the samples are inside the pad and clear of its skirt.
      for (corner in corners) {
        val inside = building.center.lerp(corner, 0.66)
        val height = groundAt(inside.x, inside.y)
        val offset = height - floor

        assertTrue(
          offset <= PAD_MAX_CUT + PAD_SLACK && offset >= -PAD_MAX_FILL - PAD_SLACK,
          "under building ${building.id} the ground is $height and the floor is $floor, which is further " +
              "than the pad is allowed to leave it"
        )

        samples++
        if (Math.abs(offset) < LEVEL_TOLERANCE) level++
      }
    }

    assertTrue(
      level >= samples * LEVEL_MAJORITY,
      "only $level of $samples samples under a building are within ${LEVEL_TOLERANCE}m of its floor; the " +
          "pad is no longer levelling anything but the easiest ground"
    )
  }

  /**
   * A building materialises as blocks, and its walls are its own material.
   *
   * The end-to-end check: the world tier decided a footprint and a material, and a chunk generated
   * independently of that decision contains it. Everything between - the footprint's own axes, the wall ring,
   * the occupancy write - is covered by this one assertion failing if any of it is wrong.
   */
  @Test
  fun `a building appears in the chunk that contains it`() {
    val config = generated.config
    // The biggest ordinary building in the world. Fortifications are excluded because they are the one kind
    // that is still voxels rather than a prop - see `TownStructures.Structure.voxelised`.
    val building = buildings
      .filter {
        BuildingFunction.entries[it.attribute(BuildingChannels.FUNCTION).toInt()] !=
            BuildingFunction.FORTIFICATION
      }
      .maxByOrNull { it.halfLength * it.halfWidth }!!

    val chunkX = Math.floorDiv((building.center.x / config.voxelSize).toInt(), config.chunkSize)
    val chunkY = Math.floorDiv((building.center.y / config.voxelSize).toInt(), config.chunkSize)

    // The prop, which is now what a building *is*. This is the assertion that used to be a count of worked
    // voxels: the whole path from a `FootprintFeature` on the world tier to something a runtime can spawn runs
    // through here, and nothing else in the module tests it end to end.
    val props = generated.materializer.propsIn(chunkX, chunkY)
    val emitted = props.indices.filter { props.kindAt(it) == PropKind.BUILDING }

    assertTrue(
      emitted.isNotEmpty(),
      "the chunk containing building ${building.id} at ${building.center} emitted no building prop"
    )
    for (i in emitted) {
      assertTrue(props.radiusAt(i) > 0.0 && props.halfWidthAt(i) > 0.0, "a building prop has no footprint")
      assertTrue(props.heightAt(i) > 0.0, "a building prop has no height")
      assertTrue(props.yawAt(i).isFinite(), "a building prop has no facing")
    }

    // The floor slab is the half of a building that is still terrain, and it has to land in the same chunk.
    val columns = generated.materializer.surfaceColumns(chunkX, chunkY)
    var slab = 0
    for (localY in 0 until config.chunkSize) {
      for (localX in 0 until config.chunkSize) {
        if (BlockType.ofOrNull(columns.blockAt(localX, localY)) == BlockType.MASONRY) slab++
      }
    }

    assertTrue(
      slab > 0,
      "the chunk containing building ${building.id} at ${building.center} has no floor slab in it"
    )
  }

  @Test
  fun `streets are paved and buildings are not on them`() {
    val config = generated.config
    val streets = generated.world.features.all().filter { it.kind == FeatureKind.STREET }
    assertTrue(streets.isNotEmpty(), "no streets were laid")

    // No building footprint may contain the centreline of a street: the setback is what guarantees it, and
    // without it a house sits in the road.
    for (street in streets.take(20)) {
      for (line in street.outline()) {
        for (point in line.points) {
          val on = buildings.firstOrNull { it.contains(point.x, point.y) }
          assertTrue(on == null, "building ${on?.id} stands on a street at $point")
        }
      }
    }

    // And the street surface really is paved, which is a property of the materialiser rather than the layout.
    val street = streets.first()
    val at = street.outline().first().pointAt(street.outline().first().length * 0.5)
    val chunkX = Math.floorDiv((at.x / config.voxelSize).toInt(), config.chunkSize)
    val chunkY = Math.floorDiv((at.y / config.voxelSize).toInt(), config.chunkSize)
    val columns = generated.materializer.surfaceColumns(chunkX, chunkY)

    var cobbles = 0
    for (i in 0 until config.chunkSize * config.chunkSize) {
      if (columns.block[i] == BlockType.COBBLESTONE.id) cobbles++
    }
    assertTrue(cobbles > 0, "the chunk a street runs through has no paving in it")
  }

  /** Minimum true-footprint width required on each side of a boundary for a crossing to count. */
  private val MIN_CROSSING_METRES = 2.0

  /**
   * Two chunks either side of a building's border agree about it.
   *
   * The seam property, for the one kind of feature that writes blocks above the ground rather than shaping it.
   * A building whose wall ring were computed from anything chunk-local would differ here, and the difference
   * would be a doorway on one side and masonry on the other.
   */
  @Test
  fun `a building spanning a chunk border is the same building from both sides`() {
    val config = generated.config
    val extent = config.chunkExtent

    // On the real oriented rectangle (`corners()`), not `bbox`: the bbox is the rotated rectangle's
    // axis-aligned bounding box, widened further by a cosmetic height-profile skirt that has no bearing on
    // where masonry gets written, so it can - and, at this seed's original version, did - claim a crossing
    // the footprint `TownStructures` actually materialises does not reach. A minimum crossing width on each
    // side keeps a razor-thin genuine crossing from being just as flaky.
    var boundaryChunk = 0
    val spanning = buildings.firstOrNull { building ->
      val xs = building.corners().map { it.x }
      val trueMinX = xs.min()
      val trueMaxX = xs.max()
      val minChunk = Math.floor(trueMinX / extent).toInt()
      val maxChunk = Math.floor(trueMaxX / extent).toInt()
      if (minChunk == maxChunk) return@firstOrNull false

      val boundary = maxChunk * extent
      val crossesCleanly = (boundary - trueMinX) >= MIN_CROSSING_METRES &&
          (trueMaxX - boundary) >= MIN_CROSSING_METRES
      if (crossesCleanly) boundaryChunk = maxChunk
      crossesCleanly
    } ?: return

    val chunkX = boundaryChunk
    val chunkY = Math.floor(spanning.center.y / extent).toInt()

    // Materialise the same column twice from two independently built chunks and compare. The shared column is
    // the western chunk's last and the eastern chunk's first.
    val west = generated.materializer.materialize(ChunkPos(chunkX - 1, chunkY, config.chunkZOf(
      spanning.attribute(BuildingChannels.FLOOR_ELEVATION)
    )))
    val east = generated.materializer.materialize(ChunkPos(chunkX, chunkY, config.chunkZOf(
      spanning.attribute(BuildingChannels.FLOOR_ELEVATION)
    )))

    assertEquals(west.chunk.z, east.chunk.z, "the two slabs are not at the same height")

    // Not the same columns - they are different places - but both must contain the building, since it crosses
    // the border between them.
    //
    // Masonry alone now, and what it finds is the **floor slab**: the walls are a prop, and a prop belongs to
    // exactly one chunk by construction. The slab is the part that still spans the border, so it is what tests
    // the property this case is actually about - two independently materialised chunks agreeing about one
    // structure's geometry at the seam between them.
    fun workedCount(chunk: net.bestia.worldgen.voxel.VoxelChunk): Int {
      var count = 0
      for (i in chunk.blocks.indices) {
        if (BlockType.ofOrNull(chunk.blocks[i].toInt()) == BlockType.MASONRY) count++
      }
      return count
    }

    assertTrue(
      workedCount(west) > 0 && workedCount(east) > 0,
      "a building crossing a chunk border appears in only one of the two chunks"
    )
  }

  /**
   * Terrain height at a world position, through the chunk column source.
   *
   * Through the *chunk* tier rather than the coarse elevation layer on purpose: what a building's pad has to
   * level is the height a chunk will actually generate, features and detail noise included, and the coarse
   * layer is neither.
   */
  private fun groundAt(x: Double, y: Double): Double {
    val config = generated.config
    val voxelX = Math.floor(x / config.voxelSize).toInt()
    val voxelY = Math.floor(y / config.voxelSize).toInt()

    val heights = generated.columns.heights(
      ChunkPos(Math.floorDiv(voxelX, config.chunkSize), Math.floorDiv(voxelY, config.chunkSize)), 0
    )
    return heights[Math.floorMod(voxelX, config.chunkSize), Math.floorMod(voxelY, config.chunkSize)]
  }

  private companion object {
    /** Metres the ground under a building may differ from its floor. One voxel of slack. */
    const val LEVEL_TOLERANCE = 1.0

    /**
     * Mirrors `TownStage.PAD_MAX_CUT` and `PAD_MAX_FILL`, which are private.
     *
     * Duplicated rather than opened up, on `WasteTombTest.TOMB_OFFSET_ALLOWANCE`'s argument: these are the
     * *bound* on the assertion rather than the value under test, so changing them over there should make
     * somebody look at this line instead of silently widening it.
     */
    const val PAD_MAX_CUT = 4.0
    const val PAD_MAX_FILL = 3.0

    /** One voxel, for the rounding between a pad's profile and the column the materialiser samples. */
    const val PAD_SLACK = 1.0

    /** Share of samples that must be level to within [LEVEL_TOLERANCE]. See the test's own KDoc. */
    const val LEVEL_MAJORITY = 0.8
  }
}
