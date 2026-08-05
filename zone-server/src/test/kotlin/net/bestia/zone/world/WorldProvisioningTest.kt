package net.bestia.zone.world

import net.bestia.worldgen.core.Order
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.store.PipelineVersion
import net.bestia.worldgen.voxel.RleCodec
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationEventPublisher
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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

  /**
   * Records what was published instead of delivering it.
   *
   * The real publisher would reach `MasterWorldResetListener`, which moves every master in the database to
   * the new spawn - a side effect on state these tests share with every other test in the same Spring
   * context, to prove something none of them are about. What is worth asserting here is that the event goes
   * out at all.
   */
  private class RecordingPublisher : ApplicationEventPublisher {
    val published = mutableListOf<Any>()
    override fun publishEvent(event: Any) {
      published.add(event)
    }
  }

  private val events = RecordingPublisher()

  @BeforeEach
  fun clean() {
    worldRepository.deleteAll()
    events.published.clear()
  }

  /**
   * A service that refuses on a mismatch, whatever the running configuration does.
   *
   * The shipped development configuration is `REGENERATE`, which would answer every one of these tests by
   * silently making the problem go away - so the tests that are about *detecting* a bad world have to state
   * the policy they are testing rather than inherit it.
   */
  private fun refusingService() =
    WorldService(provisioning, settings.copy(onMismatch = WorldGenConfig.OnMismatch.REFUSE), events)

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

    val failure = assertFailsWith<IncompatibleWorldException> { refusingService().load() }

    assertTrue(failure.message!!.contains("pipeline"), "the reason should name what disagreed: ${failure.message}")
    assertTrue(failure.message!!.contains(alien.name))
  }

  @Test
  fun `a palette change is reported as a palette change and not as different terrain`() {
    // Three separately diagnosable components rather than one opaque number, because "your world is
    // incompatible" is not something anybody can act on. Same terrain made of the wrong rock is a different
    // problem, with a different fix, from different terrain.
    worldRepository.save(worldOf(seed = 99L, widthCells = 64, heightCells = 64, blockPaletteVersion = 1234L))

    val failure = assertFailsWith<IncompatibleWorldException> { refusingService().load() }

    assertTrue(
      failure.message!!.contains("palette"),
      "a palette mismatch should say so, not blame the pipeline: ${failure.message}"
    )
  }

  @Test
  fun `a world whose row cannot rebuild its own config refuses whatever the policy says`() {
    // The bug this exists for actually shipped: `wrapX`/`wrapY` decided where the coastline went, lived in
    // WorldConfig, and had no column here - so every stored world rebuilt as a world with the default wrap
    // and nothing said a word. A shape version written at birth and recomputed from the columns is what
    // makes that visible.
    //
    // Simulated by storing a shape that the columns cannot produce, which is exactly what a missing column
    // looks like from here.
    worldRepository.save(worldOf(seed = 99L, widthCells = 64, heightCells = 64, shapeVersion = 7L))

    // REGENERATE and not REFUSE, because the point is that no policy gets to skip this one: writing the row
    // again would write the same incomplete row, and a `REGENERATE` server would destroy its world on every
    // single boot for a bug that regenerating cannot fix.
    val service = WorldService(
      provisioning,
      settings.copy(onMismatch = WorldGenConfig.OnMismatch.REGENERATE),
      events
    )

    val failure = assertFailsWith<IncompleteWorldRecordException> { service.load() }

    assertTrue(failure.message!!.contains("PersistedWorld"), "say where the field is missing from")
    assertEquals(1, worldRepository.count(), "the world must not have been discarded")
  }

  @Test
  fun `REGENERATE replaces a world the configuration has moved on from`() {
    val stored = worldRepository.save(worldOf(seed = 4242L, widthCells = 64, heightCells = 64))
    assertNotEquals(settings.widthCells, stored.widthCells, "the test is vacuous unless these differ")

    val service = WorldService(
      provisioning,
      settings.copy(onMismatch = WorldGenConfig.OnMismatch.REGENERATE),
      events
    )
    service.load()

    assertEquals(1, worldRepository.count(), "the old world should have been replaced, not joined")
    assertEquals(settings.widthCells, service.record.widthCells, "the new world follows the configuration")
    assertNotEquals(stored.id, service.record.id)

    // Every stored player position now points into terrain that does not exist. Announcing that is the whole
    // reason the event exists; `MasterWorldResetListener` is what acts on it.
    assertEquals(listOf<Any>(WorldRecreatedEvent(service.record)), events.published)
  }

  @Test
  fun `drift alone does not refuse the boot`() {
    // Birth settings are documented as ignored once a world exists, and a world quietly keeping its own
    // dimensions is honouring that. Turning it into a failed start would stop running servers over an edit
    // meant for the next world - so under REFUSE it is said out loud and the stored world is kept.
    val stored = worldRepository.save(worldOf(seed = 4242L, widthCells = 64, heightCells = 64))

    val service = refusingService()
    service.load()

    assertEquals(stored.id, service.record.id)
    assertEquals(64, service.record.widthCells, "generation follows the row, not the configuration")
  }

  @Test
  fun `Genesis has no previous victor, so its history mentions no Order`() {
    val world = provisioning.findOrCreate()

    assertNull(
      world.previousWinningOrder,
      "the first world a server creates has no predecessor, so no Order can have won one"
    )
    assertNull(world.winningOrder, "nothing scores a world yet, so this must not be guessed at")

    // The consequence, asserted rather than assumed: with no previous victor the generator is handed
    // `OrderInfluence.NONE` and the chronicle comes out with no Order in it at all.
    assertTrue(
      settings.paramsFor(world.previousWinningOrder).history.orderInfluence.isAbsent,
      "Genesis was handed a tuning that puts the Orders into its history"
    )
  }

  @Test
  fun `regenerating carries the outgoing world's victor into the world that replaces it`() {
    // The whole cross-incarnation mechanism, and the only piece of state in the server that survives a
    // regeneration. Everything else in the row is derived from the seed and is thrown away with it.
    val outgoing = worldRepository.save(worldOf(seed = 4242L, widthCells = 64, heightCells = 64))
    outgoing.winningOrder = Order.ETERNITY
    worldRepository.save(outgoing)

    val replacement = provisioning.recreate()

    assertEquals(
      Order.ETERNITY,
      replacement.previousWinningOrder,
      "the new world does not know which Order won the one it replaced"
    )
    assertNull(replacement.winningOrder, "the replacement's own fate has not been decided yet")
    assertEquals(1, worldRepository.count(), "the old world should have been replaced, not joined")
  }

  @Test
  fun `a world with no recorded victor carries nothing forward`() {
    // The case that actually happens today, since nothing scores a world yet. It has to behave exactly like
    // Genesis rather than picking a default, or every regeneration would invent a winner.
    worldRepository.save(worldOf(seed = 4242L, widthCells = 64, heightCells = 64))

    val replacement = provisioning.recreate()

    assertNull(replacement.previousWinningOrder)
    assertTrue(settings.paramsFor(replacement.previousWinningOrder).history.orderInfluence.isAbsent)
  }

  @Test
  fun `the previous victor is part of the world's identity, not a runtime setting`() {
    /*
     * The property that makes the carry-forward safe, and the reason `paramsFor` is a function of the row.
     *
     * A world generated after Chaos won is a *different world* from the same seed generated after Eternity won -
     * its towns were founded by peoples with different convictions, so different ones were forsaken and
     * different shrines stand on the map. Folding that into `pipelineVersion` is what makes the boot gate say so
     * instead of serving one world's chunks against the other's history.
     */
    val config = settings.copy(widthCells = 64, heightCells = 64).toWorldConfig(4242L)

    val none = StandardWorld.pipeline(config, settings.paramsFor(null)).pipelineVersion
    val chaos = StandardWorld.pipeline(config, settings.paramsFor(Order.CHAOS)).pipelineVersion
    val eternity = StandardWorld.pipeline(config, settings.paramsFor(Order.ETERNITY)).pipelineVersion

    assertNotEquals(none, chaos, "an Order-shaped world versions the same as an Order-free one")
    assertNotEquals(chaos, eternity, "two different victors produce the same pipeline version")
  }

  /** A stored world whose version vector is correct for this build unless a field is overridden. */
  private fun worldOf(
    seed: Long,
    widthCells: Int,
    heightCells: Int,
    pipelineVersion: Long? = null,
    blockPaletteVersion: Long? = null,
    shapeVersion: Long? = null
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
      wrapX = settings.wrapX,
      wrapY = settings.wrapY,
      pipelineVersion = pipelineVersion ?: current.pipelineVersion,
      blockPaletteVersion = blockPaletteVersion ?: current.blockPaletteVersion,
      chunkFormatVersion = current.chunkFormatVersion,
      shapeVersion = shapeVersion ?: config.shapeVersion,
      createdAt = java.time.Instant.now()
    )
  }
}
