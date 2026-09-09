package net.bestia.zone.world.prop

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.derived.DerivedStore
import net.bestia.zone.ecs.EntityAOIService
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.ecs.movement.GroundHeight
import net.bestia.zone.ecs.prop.PropPose
import net.bestia.zone.ecs.prop.StaticVisual
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.SMSG
import net.bestia.zone.socket.ChunkFanOut
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.stream.ChunkService
import net.bestia.zone.world.stream.ChunkStreamConfig
import net.bestia.zone.world.stream.ChunkSubscriptionService
import net.bestia.zone.world.stream.StaticEntityRemovedSMSG
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What happens to a tree when the ground under it is dug away.
 *
 * Against a stub prop source and a stubbed ground height for `WorldObjectResidencyTest`'s reason: every
 * property here is about the decision - which props are re-checked, which are spared, and what is recorded -
 * and a real world would make the answers depend on where the trees happen to be.
 */
class PropSupportSystemTest {

  private val column = ChunkPos(4, 5, 0)

  /** Where the stub source plants its tree, and the elevation it was planted at. */
  private val treeZ = 64L

  private lateinit var subscriptions: ChunkSubscriptionService
  private lateinit var residency: WorldObjectResidencyService
  private lateinit var divergence: RecordingDivergence
  private lateinit var removals: MutableList<StaticEntityRemovedSMSG>

  /** The `onChunkChanged` listener the system registers, so a carve can be simulated by calling it. */
  private lateinit var onChanged: (ChunkPos) -> Unit

  private var groundZ: Long? = treeZ
  private var stale = false

  private class StubSource(private val kind: StaticEntityKind, private val z: Long) : WorldObjectSource {
    override val kinds = setOf(kind)

    override fun sitesIn(chunk: ChunkPos): List<WorldObjectSite> {
      return listOf(
        WorldObjectSite(
          kind = kind,
          propId = 77L,
          position = Vec3L(chunk.x * 32L, chunk.y * 32L, z),
          variant = 0,
          heightDm = 80,
          yaw = 0f
        )
      )
    }
  }

  /** Records depletions rather than writing them, and answers `of` from what it recorded. */
  private class RecordingDivergence : WorldObjectDivergenceRegistry(mockk(), mockk(), mockk(relaxed = true)) {
    val recorded = HashMap<Long, DivergenceEntry>()

    override fun of(propId: Long): DivergenceEntry? = recorded[propId]

    override fun recordDepletion(propId: Long, kind: StaticEntityKind, resumeAt: Instant?) {
      recorded[propId] = DivergenceEntry(kind, DivergenceState.DEPLETED, resumeAt)
    }
  }

  private fun systemFor(kind: StaticEntityKind = StaticEntityKind.TREE): PropSupportSystem {
    subscriptions = ChunkSubscriptionService()
    divergence = RecordingDivergence()
    removals = ArrayList()

    val fanOut = object : ChunkFanOut {
      override fun fanOut(accountIds: Collection<Long>, message: SMSG): Int {
        if (message is StaticEntityRemovedSMSG) removals.add(message)
        return accountIds.size
      }
    }

    val worldService: WorldService = mockk {
      every { config } returns WorldConfig(seed = 1L, chunkSize = 32, voxelSize = 1.0)
      every { record } returns mockk { every { pipelineVersion } returns 1L }
    }

    residency = WorldObjectResidencyService(
      listOf(StubSource(kind, treeZ)),
      PropKindRegistry().also { it.load() },
      EntityAOIService(),
      fanOut,
      worldService,
      divergence,
      subscriptions
    )

    val derived: DerivedStore = mockk { every { isStale(any()) } answers { stale } }
    val listener = slot<(ChunkPos) -> Unit>()
    val chunkService: ChunkService = mockk {
      every { isReady } returns true
      every { derived() } returns derived
      every { onChunkChanged(capture(listener)) } answers { }
    }

    val ground = GroundHeight { groundZ }

    val system = PropSupportSystem(residency, divergence, ground, chunkService)
    onChanged = listener.captured

    return system
  }

  private fun withResidentProp(kind: StaticEntityKind = StaticEntityKind.TREE): Pair<PropSupportSystem, World> {
    val system = systemFor(kind)
    val world = testWorld()

    subscriptions.markSent(1L, column)
    residency.drain(world, budget = 8)
    assertEquals(1, residency.residentEntities, "the stub plants exactly one prop in this column")

    return system to world
  }

  @BeforeEach
  fun reset() {
    groundZ = treeZ
    stale = false
  }

  @Test
  fun `a tree whose ground has dropped away is destroyed, terminally, and its holders told`() {
    val (system, world) = withResidentProp()

    groundZ = treeZ - 10
    onChanged(column)
    system.update(world, 0.05f)

    assertEquals(0, residency.residentEntities, "the tree stood over a hole and should be gone")
    assertEquals(1, removals.size, "its holders have to be told, or they draw a prop nobody can pick up")

    val entry = divergence.recorded[77L]
    assertEquals(DivergenceState.DEPLETED, entry?.state)
    assertNull(
      entry?.resumeAt,
      "terminal on purpose: a tree that grew back over an open shaft would be the same bug on a timer"
    )
  }

  @Test
  fun `a tree the carve did not undermine is left standing`() {
    val (system, world) = withResidentProp()

    // The column was edited - somewhere else in it, or below the trunk - and the surface has not moved.
    onChanged(column)
    system.update(world, 0.05f)

    assertEquals(1, residency.residentEntities, "an edit in the column is not an edit under every tree in it")
    assertTrue(removals.isEmpty())
  }

  @Test
  fun `a column whose tiles have not been rebuilt yet waits rather than answering from a stale one`() {
    val (system, world) = withResidentProp()

    groundZ = treeZ - 10
    stale = true

    onChanged(column)
    system.update(world, 0.05f)
    assertEquals(1, residency.residentEntities, "a stale tile has no answer worth acting on")

    // And the column was kept, not dropped: the rebuild lands and the next tick judges it.
    stale = false
    system.update(world, 0.05f)
    assertEquals(0, residency.residentEntities, "the column must still have been queued")
  }

  @Test
  fun `no answer at all counts as supported`() {
    val (system, world) = withResidentProp()

    // Off the grid, or a column the store cannot speak for. Felling a wood because the answer had not arrived
    // is the worse mistake by a long way - a destroyed prop is durable, a spared one is re-checked.
    groundZ = null

    onChanged(column)
    system.update(world, 0.05f)

    assertEquals(1, residency.residentEntities)
  }

  @Test
  fun `a building is left to its own masonry`() {
    val (system, world) = withResidentProp(StaticEntityKind.BUILDING_FARM)

    groundZ = treeZ - 10
    onChanged(column)
    system.update(world, 0.05f)

    assertEquals(
      1,
      residency.residentEntities,
      "a building's walls are voxels and are still standing; undermining a house is a different event"
    )
  }

  @Test
  fun `a prop already used up this tick is not depleted twice`() {
    val (system, world) = withResidentProp()

    // What a collect or a felling blow leaves behind, reached in the same tick.
    divergence.recordDepletion(77L, StaticEntityKind.TREE, resumeAt = Instant.now().plusSeconds(600))

    groundZ = treeZ - 10
    onChanged(column)
    system.update(world, 0.05f)

    assertNotNull(
      divergence.recorded[77L]?.resumeAt,
      "the first writer wins, so the regrowth the collect recorded must survive"
    )
    assertEquals(1, residency.residentEntities, "and the prop is left for whoever claimed it to finish")
  }
}
