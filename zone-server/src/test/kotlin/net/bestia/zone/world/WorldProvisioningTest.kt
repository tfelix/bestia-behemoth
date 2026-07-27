package net.bestia.zone.world

import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.store.PipelineVersion
import net.bestia.worldgen.voxel.RleCodec
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Boot-time world detection, against the real repository.
 *
 * The behaviour under test is "generate a world only if there is not one already", which is exactly the kind of
 * thing that works in development forever without being right: the dev datasource is an in-memory H2 recreated
 * on every boot, so the *found* branch never runs there. These tests run it.
 */
@SpringBootTest
@ActiveProfiles("no-socket", "test")
class WorldProvisioningTest {

  @Autowired
  private lateinit var provisioning: WorldProvisioning

  @Autowired
  private lateinit var worldRepository: WorldRepository

  @Autowired
  private lateinit var settings: WorldGenConfig

  @BeforeEach
  fun clean() {
    worldRepository.deleteAll()
  }

  @Test
  fun `the first boot creates Genesis and records how it was made`() {
    val world = provisioning.findOrCreate()

    assertEquals(PersistedWorld.GENESIS, world.name)
    assertEquals(settings.widthCells, world.widthCells)
    assertEquals(settings.heightCells, world.heightCells)
    assertNotNull(world.createdAt)

    // Not just the dimensions: the version vector of the pipeline that made it, which is what lets a later boot
    // notice that this build would generate something else.
    assertNotEquals(0L, world.pipelineVersion)
    assertNotEquals(0L, world.blockPaletteVersion)
    assertEquals(RleCodec.VERSION, world.chunkFormatVersion)
  }

  @Test
  fun `a second boot finds the existing world instead of making another`() {
    val first = provisioning.findOrCreate()
    val second = provisioning.findOrCreate()

    assertEquals(first.id, second.id)
    assertEquals(1, worldRepository.count(), "a second world was created")
  }

  @Test
  fun `an existing world keeps its own dimensions even when the configuration has moved on`() {
    // The safe direction for this setting to fail in. A stored world's chunks - and any player edits over them -
    // were built against the dimensions in its row, so editing the config file must not silently reshape it.
    val stored = worldRepository.save(worldOf(seed = 4242L, widthCells = 32, heightCells = 32))
    assertNotEquals(settings.widthCells, stored.widthCells, "the test is vacuous unless these differ")

    val found = provisioning.findOrCreate()

    assertEquals(32, found.widthCells)
    assertEquals(4242L, found.seed)
    assertEquals(32, found.toWorldConfig().widthCells, "generation follows the row, not the config")
  }

  @Test
  fun `the world is generated from the stored seed, so it is the same world every boot`() {
    val world = provisioning.findOrCreate()
    val config = world.toWorldConfig()

    // Small, because this generates two worlds and the point is reproducibility rather than size.
    val small = config.copy(widthCells = 64, heightCells = 64)
    val a = StandardWorld.build(small)
    val b = StandardWorld.build(small)

    assertEquals(a.world.pipelineVersion, b.world.pipelineVersion)
    assertEquals(a.world.features.all().size, b.world.features.all().size)
    assertEquals(
      a.base.heightAt(1000.0, 1000.0),
      b.base.heightAt(1000.0, 1000.0),
      0.0,
      "the same seed produced different terrain"
    )
  }

  @Test
  fun `booting against a world from another pipeline refuses rather than moving the ground`() {
    // Simulates shipping a pipeline change to a live world. The stored world's edits are deltas over its old
    // base, so regenerating against a new one would put buildings in the air and holes in floors - and the old
    // base is gone by then, so there is nothing left to migrate from.
    val alien = worldRepository.save(
      worldOf(seed = 99L, widthCells = 64, heightCells = 64, pipelineVersion = 0xDEADBEEFL)
    )

    val service = WorldService(provisioning, settings)
    val failure = assertFailsWith<IncompatibleWorldException> { service.load() }

    assertTrue(failure.message!!.contains("pipeline"), "the reason should name what disagreed: ${failure.message}")
    assertTrue(failure.message!!.contains(alien.name))
  }

  @Test
  fun `a palette change is reported as a palette change and not as different terrain`() {
    // Three separately diagnosable components rather than one opaque number, because "your world is
    // incompatible" is not something anybody can act on. Same terrain made of the wrong rock is a different
    // problem, with a different fix, from different terrain.
    worldRepository.save(worldOf(seed = 99L, widthCells = 64, heightCells = 64, blockPaletteVersion = 1234L))

    val failure = assertFailsWith<IncompatibleWorldException> { WorldService(provisioning, settings).load() }

    assertTrue(
      failure.message!!.contains("palette"),
      "a palette mismatch should say so, not blame the pipeline: ${failure.message}"
    )
  }

  /** A stored world whose version vector is correct for this build unless a field is overridden. */
  private fun worldOf(
    seed: Long,
    widthCells: Int,
    heightCells: Int,
    pipelineVersion: Long? = null,
    blockPaletteVersion: Long? = null
  ): PersistedWorld {
    val config = settings.copy(widthCells = widthCells, heightCells = heightCells).toWorldConfig(seed)
    val current = PipelineVersion.current(StandardWorld.pipeline(config).pipelineVersion)

    return PersistedWorld(
      name = "TestWorld",
      seed = seed,
      widthCells = widthCells,
      heightCells = heightCells,
      cellSizeMetres = settings.cellSizeMetres,
      chunkSize = settings.chunkSize,
      chunkHeight = settings.chunkHeight,
      voxelSizeMetres = settings.voxelSizeMetres,
      seaLevelMetres = settings.seaLevelMetres,
      pipelineVersion = pipelineVersion ?: current.pipelineVersion,
      blockPaletteVersion = blockPaletteVersion ?: current.blockPaletteVersion,
      chunkFormatVersion = current.chunkFormatVersion,
      createdAt = java.time.Instant.now()
    )
  }
}
