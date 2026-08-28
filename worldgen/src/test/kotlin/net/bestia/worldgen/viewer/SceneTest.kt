package net.bestia.worldgen.viewer

import net.bestia.worldgen.core.BaseHeightField
import net.bestia.worldgen.core.CellRegion
import net.bestia.worldgen.core.ChunkHeightSampler
import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.FeatureStore
import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.IntLayer
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.Resolution
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.render.Ramps
import net.bestia.worldgen.render.Viewport
import java.awt.GraphicsEnvironment
import java.nio.file.Files
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SceneTest {

  private val config = WorldConfig(
    seed = 0x5CE1EL,
    widthCells = 8,
    heightCells = 8,
    chunkSize = 32,
    voxelSize = 1.0
  )

  private val terrain = BaseHeightField { x, y -> 100.0 + x * 0.01 - y * 0.004 }

  private fun sampler() = ChunkHeightSampler(config, terrain, FeatureStore().apply { freeze() })

  @Test
  fun `the chunk field reports exactly what the chunk sampler produced`() {
    // The viewer has to show generated output, not an approximation of it. If these ever diverge,
    // every conclusion drawn from looking at the chunk view is worthless.
    val source = sampler()
    val field = ChunkHeightField(config, source, ElevationPalette())

    for (chunk in listOf(ChunkPos(0, 0), ChunkPos(3, -2), ChunkPos(-1, 5))) {
      val heights = source.heights(chunk)
      for ((localX, localY) in listOf(0 to 0, 5 to 17, 31 to 31)) {
        val (worldX, worldY) = config.columnCenter(chunk, localX, localY)
        assertEquals(heights[localX, localY], field.valueAt(worldX, worldY), 0.0, "$chunk ($localX,$localY)")
      }
    }
  }

  @Test
  fun `the chunk field refuses to render a view it would have to generate a million chunks for`() {
    val field = ChunkHeightField(config, sampler(), ElevationPalette(), chunkBudget = 64)

    val closeUp = Viewport(0.0, 0.0, 1.0, 200, 200)
    val wholeWorld = Viewport.fit(config.worldBounds, 800, 800)

    assertNull(field.availabilityFor(closeUp))
    assertNotNull(field.availabilityFor(wholeWorld), "a world-wide chunk view must be refused")
  }

  @Test
  fun `the chunk field caches instead of regenerating every column`() {
    val field = ChunkHeightField(config, sampler(), ElevationPalette())

    for (i in 0 until 400) {
      field.valueAt(i * 0.05, i * 0.05)
    }

    // 400 samples inside a 20x20 m square: one chunk, generated once.
    assertEquals(1, field.cachedChunks())
  }

  @Test
  fun `a raster field reads its cells and reports a hole outside the raster`() {
    val region = CellRegion.world(4, 4, Resolution.KILOMETRE)
    val layer = FloatLayer(LayerId.ELEVATION, region, FloatArray(16) { (it % 4) * 50f })
    val field = FloatLayerField(layer, ElevationPalette(), interpolation = Interpolation.NEAREST)

    assertEquals(100.0, field.valueAt(2500.0, 1500.0), 1e-9)
    assertTrue(field.valueAt(-10.0, 1500.0).isNaN(), "outside the raster must be a hole")
    assertTrue(field.valueAt(1500.0, 9_000.0).isNaN())
  }

  @Test
  fun `an int layer is never interpolated`() {
    val region = CellRegion.world(4, 4, Resolution.KILOMETRE)
    val layer = IntLayer(LayerId.BIOME, region, IntArray(16) { it % 4 })
    val field = IntLayerField(layer)

    // Anywhere inside a cell gives that cell's id - no halfway value between biome 1 and biome 2.
    for (offset in doubleArrayOf(10.0, 500.0, 990.0)) {
      assertEquals(1.0, field.valueAt(1000.0 + offset, 1500.0), 0.0)
    }
    assertEquals("2", field.format(field.valueAt(2500.0, 1500.0)))
  }

  @Test
  fun `the difference field subtracts and propagates holes`() {
    val a = object : ScalarField {
      override val name = "a"
      override val palette = ContinuousPalette(Ramps.VIRIDIS)
      override fun valueAt(worldX: Double, worldY: Double) = 10.0
    }
    val b = object : ScalarField {
      override val name = "b"
      override val palette = ContinuousPalette(Ramps.VIRIDIS)
      override fun valueAt(worldX: Double, worldY: Double) = if (worldX < 0) Double.NaN else 4.0
    }

    val difference = DifferenceField(a, b)

    assertEquals(6.0, difference.valueAt(1.0, 1.0), 1e-9)
    assertTrue(difference.valueAt(-1.0, 1.0).isNaN())
  }

  @Test
  fun `a scene built from the real pipeline offers every tier as a field`() {
    val scene = pipelineScene()

    // The composed map is first, and that is behaviour rather than tidiness: WorldViewPanel selects
    // fields.first(), so this is what the viewer opens on.
    assertEquals("world map", scene.fields.first().name)
    assertTrue(scene.fields.first() is CompositeField)

    // One field per raster layer, and no stage is named anywhere in the viewer to make that happen.
    for (id in listOf(LayerId.ELEVATION, LayerId.BIOME, LayerId.PRECIPITATION, LayerId.DISCHARGE)) {
      assertNotNull(scene.fields.firstOrNull { it.name == id.name }, "no field for $id")
    }

    // And the fields that exist only because there is something below the raster tier.
    for (derived in listOf("base height", "chunk heights", "chunk - raster", "erosion", "surface block")) {
      assertNotNull(scene.fields.firstOrNull { it.name == derived }, "no '$derived' field")
    }

    assertTrue(scene.features.size > 0, "the pipeline produced no vector features")
    assertNotNull(scene.chunkSource)
  }

  @Test
  fun `the real pipeline's world has no chunk seams`() {
    // The viewer's own scene is held to the same standard as the pipeline. If this fails, either a stage
    // broke purity or the vector tier did, and both are worth a red test.
    val scene = pipelineScene()

    val report = scene.seamCheck(Viewport.fit(scene.bounds, 256, 256))

    assertNotNull(report)
    assertTrue(report.isClean, report.toString() + report.seams.take(3).joinToString("\n", "\n"))
    assertTrue(report.columnsCompared > 0)
  }

  @Test
  fun `the feature summary names what the pipeline produced`() {
    val summary = pipelineScene().featureSummary()

    assertTrue(summary.contains("river_channel"), "summary was '$summary'")
  }

  @Test
  fun `the viewer window builds`() {
    // A smoke test, not a UI test: it catches the layout and wiring mistakes that would otherwise only
    // show up as a stack trace the first time somebody actually opens the tool. Skipped where there is no
    // display - the export test covers that case.
    if (GraphicsEnvironment.isHeadless()) return

    val scene = pipelineScene()
    var failure: Throwable? = null

    SwingUtilities.invokeAndWait {
      try {
        ViewerFrame(scene).dispose()
      } catch (t: Throwable) {
        failure = t
      }
    }

    val thrown = failure
    if (thrown != null) throw thrown
  }

  @Test
  fun `the export budget lets the voxel fields render at exactly one pixel per voxel`() {
    // The regression this guards: with only the interactive budget, a 1400x1400 export of 'surface block'
    // needs ~2000 chunks, is refused, and gets halved to 0.5 m/px - a sub-voxel picture of a quarter of the
    // area that looks identical to a 1:1 one. The scale a voxel picture was measured at is not optional.
    val scene = pipelineScene()
    val voxelView = Viewport(0.0, 0.0, scene.config.voxelSize, 1400, 1400)
    val surface = scene.field("surface block")

    assertNotNull(surface.availabilityFor(voxelView), "the interactive budget should refuse this view")

    ViewerExport.withVoxelScaleBudget(scene, 1400, 1400) {
      assertNull(surface.availabilityFor(voxelView), "the export budget must allow a 1:1 voxel view")
      assertNull(scene.field("surface fill").availabilityFor(voxelView))
    }

    // And put back, so exporting a scene cannot leave the interactive viewer able to hang itself.
    assertNotNull(surface.availabilityFor(voxelView), "the budget must be restored after the export")
  }

  @Test
  fun `every field renders to a png without a display`() {
    // The headless half has to keep working: it is what runs over SSH and in CI.
    val scene = pipelineScene()
    val directory = Files.createTempDirectory("worldgen-viewer-test").toFile()

    try {
      val written = ViewerExport.exportAll(scene, directory, widthPx = 160, heightPx = 160)

      val names = written.map { it.nameWithoutExtension }.toSet()

      // One per field, and one more: the region overlay, which is not a field. Named explicitly rather
      // than folded into the count, so a future extra picture fails here with something to read instead
      // of an off-by-one.
      assertTrue(names.contains("place-regions"), "the region overlay is missing; wrote $names")
      assertEquals(scene.fields.size + 1, written.size, "wrote $names")

      for (file in written) {
        assertTrue(file.length() > 0, "${file.name} is empty")
      }
    } finally {
      directory.deleteRecursively()
    }
  }

  private companion object {
    /**
     * One world shared by every test that needs one.
     *
     * Small, but a real run of the real pipeline rather than a stub. A viewer tested against fake data is
     * a viewer that can disagree with the pipeline without any test noticing, which defeats the point of
     * having it.
     */
    private val scene by lazy {
      WorldScene.of(
        StandardWorld.build(
          // Large enough to be sure of having rivers in it; see the note on StandardWorldTest.config.
          WorldConfig(seed = 0x5CE1EL, widthCells = 160, heightCells = 160, chunkSize = 32, voxelSize = 1.0)
        )
      )
    }

    fun pipelineScene() = scene
  }
}
