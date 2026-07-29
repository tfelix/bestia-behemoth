package net.bestia.worldgen.viewer

import net.bestia.worldgen.pipeline.StandardWorld
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class WorldArgsTest {

  private val demo = StandardWorld.demoConfig()

  /**
   * The flags `WorldGenSettings.toolArgs` produces for the shipped `worldgen:` block.
   *
   * Spelled out rather than read from the file: this test asserts that the *flags* mean what the server's
   * settings mean, and reading the settings to build the expectation would assert nothing.
   */
  private val genesisFlags = listOf(
    "--seed", "11753242",
    "--width-cells", "128",
    "--height-cells", "128",
    "--cell-size", "1000.0",
    "--chunk-size", "32",
    "--chunk-height", "256",
    "--voxel-size", "1.0",
    "--sea-level", "0.0",
    "--wrap-x", "true",
    "--wrap-y", "true"
  )

  @Test
  fun `the server's birth settings survive the trip through the flags`() {
    val config = WorldArgs(genesisFlags).worldConfig(demo)

    assertEquals(11753242L, config.seed)
    assertEquals(128, config.widthCells)
    assertEquals(128, config.heightCells)
    assertEquals(1000.0, config.baseResolution.metresPerCell)
    assertEquals(32, config.chunkSize)
    assertEquals(256, config.chunkHeight)
    assertEquals(1.0, config.voxelSize)
    assertEquals(0.0, config.seaLevel)
    assertTrue(config.wrapX)
    assertTrue(config.wrapY)
  }

  /**
   * The reason `--wrap-y` had to exist.
   *
   * `zone-server` wraps both axes and [StandardWorld.demoConfig] wraps only X, and the wrap is read by every
   * distance transform, flow route and feature the pipeline lays down. Before this flag the viewer could match
   * the server's seed and extent and still be showing different coastlines, with nothing on screen to say so.
   */
  @Test
  fun `matching only the seed and the extent is not the same world`() {
    val genesis = WorldArgs(genesisFlags).worldConfig(demo)
    val lookalike = WorldArgs(listOf("--seed", "11753242", "--cells", "128")).worldConfig(demo)

    assertNotEquals(genesis.shapeVersion, lookalike.shapeVersion)
    assertEquals(genesis.shapeVersion, WorldArgs(genesisFlags).worldConfig(demo).shapeVersion)
  }

  @Test
  fun `a later flag overrides an earlier one`() {
    // What makes `-Pgenesis -Pseed=42` mean "that world, other seed" rather than silently one or the other.
    val config = WorldArgs(genesisFlags + listOf("--seed", "42", "--width-cells", "64")).worldConfig(demo)

    assertEquals(42L, config.seed)
    assertEquals(64, config.widthCells)
    assertEquals(128, config.heightCells, "unmentioned edges keep the world they came from")
  }

  @Test
  fun `flags the tool does not know are refused rather than ignored`() {
    // The whole point: a mistyped flag used to produce a confident answer about the wrong world.
    val failure = assertFailsWith<IllegalArgumentException> {
      WorldArgs(listOf("--cells", "128", "--wrapy", "true"))
    }
    assertTrue(failure.message!!.contains("--wrapy"), failure.message!!)
  }

  @Test
  fun `a flag a tool declares for itself is accepted`() {
    val cli = WorldArgs(listOf("--span", "64", "--cells", "128"), extraFlags = setOf("--span"))

    assertEquals(64, cli.int("--span"))
    assertEquals(128, cli.worldConfig(demo).widthCells)
  }

  @Test
  fun `setting both edges and one edge at once is a contradiction`() {
    assertFailsWith<IllegalArgumentException> { WorldArgs(listOf("--cells", "128", "--width-cells", "64")) }
  }

  @Test
  fun `a value that is not a number is refused`() {
    assertFailsWith<IllegalArgumentException> { WorldArgs(listOf("--cells", "many")).worldConfig(demo) }
    assertFailsWith<IllegalArgumentException> { WorldArgs(listOf("--wrap-y", "yes")).worldConfig(demo) }
  }

  @Test
  fun `a flag with no value is refused`() {
    // Rather than reading the next flag as the value, or the default as though nothing was asked for.
    assertFailsWith<IllegalArgumentException> {
      WorldArgs(listOf("--seed", "--cells", "128")).worldConfig(demo)
    }
  }

  @Test
  fun `no flags at all leaves the tool's own default world alone`() {
    assertEquals(demo.shapeVersion, WorldArgs(emptyList()).worldConfig(demo).shapeVersion)
  }

  @Test
  fun `every field that decides terrain has a flag`() {
    // Guards the property the doc on WORLD_FLAGS claims. A `WorldConfig` field in `shapeVersion` with no flag
    // is a world the offline tools cannot be pointed at, which is the gap this class was written to close.
    val flagged = listOf(
      listOf("--seed", "7"),
      listOf("--width-cells", "64"),
      listOf("--height-cells", "64"),
      listOf("--cell-size", "500.0"),
      listOf("--sea-level", "10.0"),
      listOf("--chunk-size", "16"),
      listOf("--chunk-height", "128"),
      listOf("--voxel-size", "0.5"),
      listOf("--wrap-x", "false"),
      listOf("--wrap-y", "true"),
      listOf("--detail-scale", "2.0"),
      listOf("--ocean-border", "1500.0")
    )

    for (flag in flagged) {
      assertNotEquals(
        demo.shapeVersion,
        WorldArgs(flag).worldConfig(demo).shapeVersion,
        "${flag.first()} did not change which world this is"
      )
    }

    // `--cells` is the only world flag not listed above, because it is shorthand for two that are.
    assertEquals(WorldArgs.WORLD_FLAGS.size, flagged.size + 1)
  }
}
