package net.bestia.worldgen.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class WorldGenPipelineTest {

  /** A stage that fills its layer with a constant, and optionally reads a layer it should not. */
  private class TestStage(
    name: String,
    override val version: Int = 1,
    override val dependencies: List<StageId> = emptyList(),
    override val scale: StageScale = StageScale.WORLD,
    private val fill: Float = 1f,
    private val alsoRead: LayerId? = null,
    private val declareOutput: Boolean = true,
    /** A test stage has no params; the tests that care about the version vector set this explicitly. */
    override val paramsVersion: Long = 0L
  ) : Stage {

    override val id = StageId(name)
    override val resolution = Resolution.KILOMETRE
    val layer = LayerId(name)

    override val outputs =
      if (declareOutput) listOf(StageOutput.Raster(layer)) else emptyList()

    override fun generate(ctx: GenContext, region: CellRegion): StageResult {
      alsoRead?.let { ctx.layers.float(it) }
      return StageResult.of(ctx.floatLayer(layer, region, fill))
    }
  }

  private val config = WorldConfig(seed = 7L, widthCells = 8, heightCells = 8)

  @Test
  fun `stages are ordered so every dependency comes first`() {
    val tectonics = TestStage("tectonics")
    val climate = TestStage("climate", dependencies = listOf(tectonics.id))
    val hydrology = TestStage("hydrology", dependencies = listOf(climate.id, tectonics.id))

    val pipeline = WorldGenPipeline(listOf(hydrology, climate, tectonics))

    assertEquals(
      listOf("tectonics", "climate", "hydrology"),
      pipeline.stages.map { it.id.name }
    )
  }

  @Test
  fun `ordering of independent stages is stable, not input dependent`() {
    val root = TestStage("root")
    val a = TestStage("aaa", dependencies = listOf(root.id))
    val b = TestStage("bbb", dependencies = listOf(root.id))
    val c = TestStage("ccc", dependencies = listOf(root.id))

    val forward = WorldGenPipeline(listOf(root, a, b, c)).stages.map { it.id.name }
    val backward = WorldGenPipeline(listOf(c, b, a, root)).stages.map { it.id.name }

    assertEquals(forward, backward)
    assertEquals(listOf("root", "aaa", "bbb", "ccc"), forward)
  }

  @Test
  fun `a cycle is rejected with the stages named`() {
    val a = TestStage("aaa", dependencies = listOf(StageId("bbb")))
    val b = TestStage("bbb", dependencies = listOf(StageId("aaa")))

    val error = assertFailsWith<IllegalArgumentException> { WorldGenPipeline(listOf(a, b)) }
    assertTrue(error.message!!.contains("aaa"), error.message)
    assertTrue(error.message!!.contains("bbb"), error.message)
  }

  @Test
  fun `a dependency outside the pipeline is rejected`() {
    val orphan = TestStage("orphan", dependencies = listOf(StageId("missing")))

    assertFailsWith<IllegalArgumentException> { WorldGenPipeline(listOf(orphan)) }
  }

  @Test
  fun `duplicate stage ids are rejected`() {
    assertFailsWith<IllegalArgumentException> {
      WorldGenPipeline(listOf(TestStage("same"), TestStage("same")))
    }
  }

  @Test
  fun `reading an undeclared layer fails loudly`() {
    val tectonics = TestStage("tectonics")
    val climate = TestStage("climate")
    // Declares nothing, but reaches for tectonics' layer anyway.
    val sneaky = TestStage(
      "sneaky",
      dependencies = listOf(climate.id),
      alsoRead = tectonics.layer
    )

    val pipeline = WorldGenPipeline(listOf(tectonics, climate, sneaky))
    val error = assertFailsWith<IllegalStateException> { pipeline.generateWorld(config) }

    assertTrue(error.message!!.contains("did not declare"), error.message)
  }

  @Test
  fun `reading a declared layer works, including transitively`() {
    val tectonics = TestStage("tectonics", fill = 3f)
    val climate = TestStage("climate", dependencies = listOf(tectonics.id))
    // Only declares climate, but tectonics is upstream of climate so it is visible.
    val hydrology = TestStage(
      "hydrology",
      dependencies = listOf(climate.id),
      alsoRead = tectonics.layer
    )

    val world = WorldGenPipeline(listOf(tectonics, climate, hydrology)).generateWorld(config)

    assertEquals(3f, (world.layers[tectonics.layer] as FloatLayer)[0, 0])
  }

  @Test
  fun `a stage that does not produce what it declared fails`() {
    val liar = object : Stage {
      override val id = StageId("liar")
      override val version = 1
      override val paramsVersion = 0L
      override val dependencies = emptyList<StageId>()
      override val scale = StageScale.WORLD
      override val resolution = Resolution.KILOMETRE
      override val outputs = listOf(StageOutput.Raster(LayerId("promised")))
      override fun generate(ctx: GenContext, region: CellRegion) = StageResult.EMPTY
    }

    val error = assertFailsWith<IllegalStateException> {
      WorldGenPipeline(listOf(liar)).generateWorld(config)
    }
    assertTrue(error.message!!.contains("promised"), error.message)
  }

  @Test
  fun `a stage that produces an undeclared layer fails`() {
    val leaky = TestStage("leaky", declareOutput = false)

    assertFailsWith<IllegalStateException> {
      WorldGenPipeline(listOf(leaky)).generateWorld(config)
    }
  }

  @Test
  fun `bumping a version invalidates that stage and everything downstream, nothing upstream`() {
    fun build(erosionVersion: Int): WorldGenPipeline {
      val tectonics = TestStage("tectonics")
      val erosion = TestStage("erosion", version = erosionVersion, dependencies = listOf(tectonics.id))
      val biomes = TestStage("biomes", dependencies = listOf(erosion.id))
      return WorldGenPipeline(listOf(tectonics, erosion, biomes))
    }

    val before = build(1)
    val after = build(2)

    assertEquals(before.versionOf(StageId("tectonics")), after.versionOf(StageId("tectonics")))
    assertNotEquals(before.versionOf(StageId("erosion")), after.versionOf(StageId("erosion")))
    assertNotEquals(before.versionOf(StageId("biomes")), after.versionOf(StageId("biomes")))
    assertNotEquals(before.pipelineVersion, after.pipelineVersion)
  }

  @Test
  fun `the chunk cache key covers seed, pipeline version and coordinate`() {
    val pipeline = WorldGenPipeline(listOf(TestStage("only")))
    val key = pipeline.chunkCacheKey(1L, ChunkPos(4, 5))

    assertEquals(key, pipeline.chunkCacheKey(1L, ChunkPos(4, 5)))
    assertNotEquals(key, pipeline.chunkCacheKey(2L, ChunkPos(4, 5)))
    assertNotEquals(key, pipeline.chunkCacheKey(1L, ChunkPos(5, 4)))
  }

  @Test
  fun `world scale stages run and region scale stages do not`() {
    val world = TestStage("worldwide")
    val region = TestStage("regional", scale = StageScale.REGION)

    val pipeline = WorldGenPipeline(listOf(world, region))
    val result = pipeline.generateWorld(config)

    assertTrue(result.layers.contains(world.layer))
    assertTrue(!result.layers.contains(region.layer))
    assertEquals(listOf(region.id), pipeline.stagesAt(StageScale.REGION).map { it.id })
  }

  @Test
  fun `the feature store is frozen once the world tier is built`() {
    val world = WorldGenPipeline(listOf(TestStage("only"))).generateWorld(config)

    assertFailsWith<IllegalStateException> {
      world.features.add(StageId("latecomer"), emptyList())
    }
  }
}
