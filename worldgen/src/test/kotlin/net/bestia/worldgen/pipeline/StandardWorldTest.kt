package net.bestia.worldgen.pipeline

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.bio.BiomeStage
import net.bestia.worldgen.climate.ClimateStage
import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.ChunkSeamCheck
import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.IntLayer
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.Stage
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.core.WorldGenPipeline
import net.bestia.worldgen.civ.HabitabilityStage
import net.bestia.worldgen.civ.SettlementStage
import net.bestia.worldgen.geo.BoundaryType
import net.bestia.worldgen.geo.ErosionStage
import net.bestia.worldgen.geo.GlacialStage
import net.bestia.worldgen.geo.TectonicsStage
import net.bestia.worldgen.hydro.HydrologyStage
import net.bestia.worldgen.resource.ResourceStage
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.MarkerFeature
import net.bestia.worldgen.voxel.BlockType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class StandardWorldTest {

  /**
   * Small enough to generate in a fraction of a second, large enough that every stage has something to do.
   *
   * The size is load bearing, which is worth saying because it looks arbitrary. A channel needs about a
   * hundred square kilometres of catchment before its discharge crosses the threshold, so a world much
   * smaller than this can legitimately have *no rivers at all* - and then every test that asserts anything
   * about rivers passes vacuously while testing nothing.
   */
  private fun config(seed: Long) = WorldConfig(
    seed = seed,
    widthCells = 160,
    heightCells = 160,
    chunkSize = 32,
    voxelSize = 1.0
  )

  private val world by lazy { StandardWorld.build(config(0xC0FFEEL)) }

  @Test
  fun `the pipeline runs in dependency order and every stage declares what it reads`() {
    // The scoped layer store throws on an undeclared read, so a clean run *is* the assertion. What is
    // checked here is that the ordering the scheduler chose is the one the physics needs: you cannot
    // erode before you have rain, and you cannot place biomes before you have rivers.
    val order = StandardWorld.pipeline(config(1L)).stages.map { it.id }

    assertEquals(
      listOf(
        TectonicsStage.ID,
        ClimateStage.ID,
        ErosionStage.ID,
        // Glacial only needs the eroded surface and the climate, so it sorts before hydrology. Ties in the
        // topological order are broken by stage name, which is what makes this list stable rather than merely
        // valid - two nodes must execute the same order or they derive different RNG streams.
        GlacialStage.ID,
        HydrologyStage.ID,
        BiomeStage.ID,
        ResourceStage.ID,
        HabitabilityStage.ID,
        SettlementStage.ID
      ),
      order
    )
  }

  @Test
  fun `climate runs coarser than the heightfield on a real world and not on a tiny one`() {
    val real = StandardWorld.climateResolutionFor(config(1L).copy(widthCells = 512, heightCells = 512))
    val tiny = StandardWorld.climateResolutionFor(config(1L).copy(widthCells = 16, heightCells = 16))

    assertEquals(4000.0, real.metresPerCell, 1e-9)
    // A four-by-four climate grid is not a climate model, so below the threshold it is better to spend
    // the cycles than to produce a field that is sixteen numbers.
    assertEquals(1000.0, tiny.metresPerCell, 1e-9)
  }

  @Test
  fun `the world is reproducible from its seed`() {
    // The property everything else rests on. If this fails, caching is wrong, distribution is wrong, and
    // the client could never generate a matching base chunk.
    val once = StandardWorld.build(config(4242L))
    val twice = StandardWorld.build(config(4242L))

    assertEquals(once.world.pipelineVersion, twice.world.pipelineVersion)

    for (id in once.world.layers.ids()) {
      val a = once.world.layers[id]
      val b = twice.world.layers[id]
      when (a) {
        is FloatLayer -> assertTrue(a.data.contentEquals((b as FloatLayer).data), "$id differs")
        is IntLayer -> assertTrue(a.data.contentEquals((b as IntLayer).data), "$id differs")
        else -> error("unexpected layer type for $id")
      }
    }

    assertEquals(once.world.features.size, twice.world.features.size)
    assertEquals(
      once.world.features.all().map { it.id },
      twice.world.features.all().map { it.id }
    )
  }

  @Test
  fun `a different seed is a different world`() {
    val a = StandardWorld.build(config(1L))
    val b = StandardWorld.build(config(2L))

    val ea = a.world.layers.require<FloatLayer>(LayerId.ELEVATION)
    val eb = b.world.layers.require<FloatLayer>(LayerId.ELEVATION)

    assertNotEquals(ea.data.toList(), eb.data.toList())
  }

  @Test
  fun `the pipeline version isolates a stage change to that stage and below`() {
    // The point of the version vector: retuning erosion must not invalidate the expensive tectonic pass.
    val base = StandardWorld.pipeline(config(1L))
    val retunedErosion = WorldGenPipeline(
      listOf(
        TectonicsStage(),
        ClimateStage(),
        Reversioned(ErosionStage(), version = 99),
        HydrologyStage(),
        BiomeStage()
      )
    )

    assertEquals(base.versionOf(TectonicsStage.ID), retunedErosion.versionOf(TectonicsStage.ID))
    assertEquals(base.versionOf(ClimateStage.ID), retunedErosion.versionOf(ClimateStage.ID))
    assertNotEquals(base.versionOf(ErosionStage.ID), retunedErosion.versionOf(ErosionStage.ID))
    assertNotEquals(base.versionOf(HydrologyStage.ID), retunedErosion.versionOf(HydrologyStage.ID))
    assertNotEquals(base.pipelineVersion, retunedErosion.pipelineVersion)
  }

  @Test
  fun `the world has land, sea, rivers and plate boundaries`() {
    val elevation = world.world.layers.require<FloatLayer>(LayerId.ELEVATION)
    val land = elevation.data.count { it > 0f }.toDouble() / elevation.data.size

    assertTrue(land in 0.05..0.85, "land fraction was $land")

    val byKind = world.world.features.all().groupingBy { it.kind }.eachCount()
    assertTrue((byKind[FeatureKind.RIVER_CHANNEL] ?: 0) > 0, "no rivers: $byKind")
    assertTrue((byKind[FeatureKind.FAULT] ?: 0) > 0, "no plate boundaries: $byKind")
  }

  @Test
  fun `plate boundaries carry geometry but never touch terrain`() {
    // The whole reason MarkerFeature exists. A fault's bounding box spans hundreds of kilometres, so if
    // it affected height it would be evaluated by every chunk in the world for nothing.
    val faults = world.world.features.all().filter { it.kind == FeatureKind.FAULT }

    assertTrue(faults.isNotEmpty())
    for (fault in faults) {
      assertTrue(!fault.affectsHeight, "${fault.id} claims to affect height")
      assertTrue(fault.outline().isNotEmpty(), "${fault.id} has no geometry to carry")
    }

    // And the attributes are readable at a position, which is the whole point of putting them on a
    // feature rather than leaving downstream stages to re-derive boundaries from the plate raster.
    val marker = faults.filterIsInstance<MarkerFeature>().first()
    val point = marker.centerline.pointAt(marker.centerline.length * 0.5)
    val type = marker.attributeAt(marker.channel(TectonicsStage.CHANNEL_BOUNDARY_TYPE), point.x, point.y)
    val strength = marker.attributeAt(marker.channel(TectonicsStage.CHANNEL_STRENGTH), point.x, point.y)

    assertTrue(type.toInt() in BoundaryType.entries.indices, "boundary type $type is not a type")
    assertTrue(strength in 0.0..1.0, "boundary strength was $strength")
  }

  @Test
  fun `the climate produces a rain shadow rather than uniform rainfall`() {
    // Not a check on any particular mountain - which mountains a seed has is the seed's business - but on
    // the field having structure at all. A uniform precipitation field means the advection sweep is not
    // doing anything, and every biome downstream of it would be decided by temperature alone.
    val precipitation = world.world.layers.require<FloatLayer>(LayerId.PRECIPITATION)
    val low = precipitation.data.min()
    val high = precipitation.data.max()

    assertTrue(high > low * 3.0, "precipitation spans only $low..$high; the sweep is not doing anything")
  }

  @Test
  fun `every invariant holds on this world`() {
    val violations = Invariants.check(world)
    assertTrue(violations.isEmpty(), violations.joinToString("\n"))
  }

  @Test
  fun `the invariants hold across a sweep of seeds`() {
    // Worldgen bugs are rare-seed bugs. Four is not a thousand, but it is enough to catch the ones that
    // depend on a world having no lakes, or no rivers, or almost no land.
    val report = Invariants.sweep(
      seeds = 4,
      firstSeed = 700L,
      config = { seed -> config(seed).copy(widthCells = 128, heightCells = 128) }
    )

    assertTrue(report.isClean, report.toString() + "\n" + report.violations.take(6).joinToString("\n"))
  }

  @Test
  fun `chunks generated independently agree on their shared columns`() {
    // The property the three-representation split was chosen for, now checked against the real pipeline
    // with real river features crossing real chunk borders.
    val report = ChunkSeamCheck.run(world.columns, ChunkPos(48, 48), blockSize = 6, threads = 4)

    assertTrue(report.isClean, report.toString() + report.seams.take(3).joinToString("\n", "\n"))
    assertTrue(report.columnsCompared > 0)
  }

  @Test
  fun `a materialised column is solid below its surface and air above it`() {
    val config = world.config
    val chunk = world.materializer.materializeSurface(50, 50)
    val heights = world.columns.heights(chunk.chunk, 0)
    val baseZ = config.voxelBaseOf(chunk.chunk)

    var checked = 0
    for (localY in 0 until config.chunkSize) {
      for (localX in 0 until config.chunkSize) {
        val surface = heights[localX, localY]
        val surfaceZ = config.voxelZOf(surface) - baseZ
        if (surfaceZ < 1 || surfaceZ >= config.chunkHeight - 1) continue

        assertTrue(
          chunk[localX, localY, surfaceZ - 1].solid,
          "($localX,$localY) is not solid just below its surface at $surface"
        )

        // Above the terrain there may be air, or standing water, or the ice on top of it - but never rock.
        // Asserting air outright is wrong: a deep ocean column is water all the way to the top of the
        // chunk, and correctly so.
        val above = chunk[localX, localY, surfaceZ + 1]
        assertTrue(
          above in ABOVE_SURFACE,
          "($localX,$localY) has $above above its surface at $surface"
        )
        checked++
      }
    }

    assertTrue(checked > 100, "only $checked columns were testable")
  }

  @Test
  fun `water sits at the water level and never above it`() {
    val config = world.config
    val waterLevel = world.world.layers.require<FloatLayer>(LayerId.WATER_LEVEL)
    val lakeId = world.world.layers.require<IntLayer>(LayerId.LAKE_ID)

    // Find a chunk that actually has sea in it, otherwise the test asserts nothing.
    val region = waterLevel.region
    var wetCell: Pair<Int, Int>? = null
    outer@ for (y in region.minY..region.maxY) {
      for (x in region.minX..region.maxX) {
        if (!waterLevel[x, y].isNaN() && lakeId[x, y] == 0) {
          wetCell = x to y
          break@outer
        }
      }
    }
    val (cellX, cellY) = requireNotNull(wetCell) { "the test world has no sea at all" }

    val metres = region.resolution.metresPerCell
    val chunkX = ((cellX + 0.5) * metres / config.chunkExtent).toInt()
    val chunkY = ((cellY + 0.5) * metres / config.chunkExtent).toInt()

    val chunk = world.materializer.materializeSurface(chunkX, chunkY)
    val baseZ = config.voxelBaseOf(chunk.chunk)

    // The highest surface any column in this chunk could legitimately be filled to: sea level, or a lake
    // level if one of its cells belongs to a lake. Derived rather than assumed, because a chunk near a
    // shore can straddle a lake whose surface stands above the sea.
    val bounds = config.chunkBounds(chunk.chunk)
    var highestLevel = config.seaLevel
    var cy = (bounds.minY / metres).toInt()
    while (cy <= (bounds.maxY / metres).toInt()) {
      var cx = (bounds.minX / metres).toInt()
      while (cx <= (bounds.maxX / metres).toInt()) {
        val level = waterLevel[cx, cy].toDouble()
        if (!level.isNaN() && level > highestLevel) highestLevel = level
        cx++
      }
      cy++
    }

    var water = 0
    for (localY in 0 until config.chunkSize) {
      for (localX in 0 until config.chunkSize) {
        for (localZ in 0 until config.chunkHeight) {
          val block = chunk[localX, localY, localZ]
          if (block != BlockType.WATER && block != BlockType.ICE) continue

          water++
          val bottom = config.elevationOfVoxel(baseZ + localZ)
          assertTrue(
            bottom <= highestLevel + config.voxelSize,
            "water at ($localX,$localY,$localZ) sits at $bottom, above the highest level $highestLevel"
          )
        }
      }
    }

    assertTrue(water > 0, "a chunk chosen because it contains sea materialised no water")
  }

  @Test
  fun `a river channel materialises with water in it`() {
    // The gap this closes: sea and lake levels come from a raster, but a river surface descends along its
    // channel and cannot. Before the vector water source existed, every river valley in the world
    // materialised bone dry - a carved channel with nothing in it, which is the most visible way the voxel
    // tier can be wrong and exactly what the vertical slice is supposed to demonstrate.
    val config = world.config
    val river = world.world.features.all()
      .filterIsInstance<net.bestia.worldgen.vector.PolylineFeature>()
      .filter { it.kind == FeatureKind.RIVER_CHANNEL }
      // The largest reach, so the channel is comfortably wider than one voxel.
      .maxByOrNull { it.centerline.length }
    val centerline = requireNotNull(river) { "the test world has no rivers" }.centerline

    // Walk along the reach until a point turns up whose chunk actually materialises water. Not every
    // point does - a headwater channel can be under a metre deep - and a test that picked one arbitrary
    // point would be flaky for a reason that says nothing about the code.
    var wettest = 0
    var checked = 0
    var step = centerline.length * 0.1
    while (step < centerline.length) {
      val point = centerline.pointAt(step)
      step += centerline.length * 0.1

      val chunkX = Math.floorDiv(point.x.toLong(), config.chunkExtent.toLong()).toInt()
      val chunkY = Math.floorDiv(point.y.toLong(), config.chunkExtent.toLong()).toInt()
      val chunk = world.materializer.materializeSurface(chunkX, chunkY)
      checked++

      val water = chunk.countOf(BlockType.WATER) + chunk.countOf(BlockType.ICE)
      if (water > wettest) wettest = water
    }

    assertTrue(checked > 0, "no point on the reach produced a chunk")
    assertTrue(wettest > 0, "no chunk along a ${centerline.length.toInt()} m river reach contains water")
  }

  @Test
  fun `soil never appears below bedrock in a column`() {
    val chunk = world.materializer.materializeSurface(60, 44)
    val config = world.config

    for (localY in 0 until config.chunkSize step 4) {
      for (localX in 0 until config.chunkSize step 4) {
        var highestBedrock = -1
        var lowestSoil = config.chunkHeight

        for (localZ in 0 until config.chunkHeight) {
          val block = chunk[localX, localY, localZ]
          if (block in BEDROCKS) highestBedrock = localZ
          if (block in SOILS && localZ < lowestSoil) lowestSoil = localZ
        }

        // Columns with no soil at all - a cliff, deep ocean floor - are legitimate, so this only asserts
        // that where both exist, the soil is above the rock.
        if (highestBedrock >= 0 && lowestSoil < config.chunkHeight) {
          assertTrue(
            lowestSoil > highestBedrock,
            "($localX,$localY) has soil at $lowestSoil below bedrock at $highestBedrock"
          )
        }
      }
    }
  }

  @Test
  fun `biomes are all valid and the world is not one single biome`() {
    val biome = world.world.layers.require<IntLayer>(LayerId.BIOME)
    val present = biome.data.toSortedSet()

    for (ordinal in present) {
      assertTrue(ordinal in Biome.entries.indices, "biome ordinal $ordinal is not a biome")
    }
    assertTrue(present.size >= 4, "the world has only ${present.size} biomes: ${present.map { Biome.of(it) }}")
  }

  /**
   * A stage that is another stage in every respect but its version.
   *
   * For testing the version vector without making the real stages open. Interface delegation gives every
   * other member for free, which is the point - a hand-written stub would drift.
   */
  private class Reversioned(delegate: Stage, override val version: Int) : Stage by delegate

  private companion object {
    val ABOVE_SURFACE = setOf(BlockType.AIR, BlockType.WATER, BlockType.ICE)

    val SOILS = setOf(
      BlockType.DIRT, BlockType.SAND, BlockType.CLAY, BlockType.PEAT,
      BlockType.PERMAFROST, BlockType.GRASS
    )
    val BEDROCKS = setOf(
      BlockType.GRANITE, BlockType.BASALT, BlockType.LIMESTONE,
      BlockType.SANDSTONE, BlockType.SHALE, BlockType.CONGLOMERATE
    )
  }
}
