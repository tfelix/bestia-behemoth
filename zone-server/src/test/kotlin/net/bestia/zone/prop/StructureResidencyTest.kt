package net.bestia.zone.prop

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.WorldConfig
import net.bestia.zone.aoi.AoiLayer
import net.bestia.zone.aoi.EntityAOIService
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.entity.ecs.PlayerStructureIdentity
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.ChunkFanOut
import net.bestia.zone.message.SMSG
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.anchor.AnchorResidencySystem
import net.bestia.zone.world.anchor.SimulationAnchor
import net.bestia.zone.world.anchor.SimulationAnchorConfig
import net.bestia.zone.world.anchor.SimulationAnchorIndex
import net.bestia.zone.world.residency.ChunkResidency
import net.bestia.zone.world.stream.ChunkService
import net.bestia.zone.world.stream.ChunkStaticEntitiesSMSG
import net.bestia.zone.world.stream.ChunkStreamConfig
import net.bestia.zone.world.stream.ChunkSubscriptionService
import org.junit.jupiter.api.Assertions.assertEquals
import kotlin.test.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Player structures follow [ChunkResidency], so a simulation anchor keeps them in the world; trees follow clients.
 *
 * With a real [AnchorResidencySystem] and [PlayerStructureSource], so "near a bestia" is the ground an anchor holds.
 */
class StructureResidencyTest {

  private val chunkService: ChunkService = mockk {
    every { isReady } returns true
    every { config } returns WorldConfig(seed = 1L, chunkSize = 32, voxelSize = 1.0)
    every { slabEpoch } returns 0L
    every { cachedSlabsOf(any()) } returns intArrayOf(0)
    every { normalise(any()) } answers { firstArg() }
  }

  private val chunkResidency = ChunkResidency()
  private val subscriptions = ChunkSubscriptionService(chunkResidency)

  private val anchors = AnchorResidencySystem(
    chunkService,
    ChunkStreamConfig(viewRadiusChunks = 1, releaseMarginChunks = 1),
    SimulationAnchorConfig(),
    chunkResidency,
    SimulationAnchorIndex(),
  )

  private val stationsByColumn = HashMap<Pair<Int, Int>, List<StructureEntry>>()

  private val structures: PlayerStructureRegistry = mockk {
    every { `in`(any(), any()) } answers { stationsByColumn[firstArg<Int>() to secondArg<Int>()] ?: emptyList() }
  }

  private val kinds = PropKindRegistry().also { it.load() }
  private val stations = PlayerStructureSource(structures, kinds)
  private val trees = TreeSource()
  private val aoi = EntityAOIService()

  /** Each batch with the accounts it went to. */
  private val batches = ArrayList<Pair<Set<Long>, ChunkStaticEntitiesSMSG>>()

  private val fanOut = object : ChunkFanOut {
    override fun fanOut(accountIds: Collection<Long>, message: SMSG): Int {
      if (message is ChunkStaticEntitiesSMSG) batches.add(accountIds.toSet() to message)
      return accountIds.size
    }
  }

  private val worldService: WorldService = mockk {
    every { config } returns WorldConfig(seed = 1L, chunkSize = 32, voxelSize = 1.0)
    every { record } returns mockk { every { pipelineVersion } returns 1L }
  }

  private val divergence: WorldObjectDivergenceRegistry = mockk {
    every { of(any()) } returns null
  }

  private val residency = WorldObjectResidencyService(
    listOf(trees, stations), kinds, aoi, fanOut, worldService, divergence, subscriptions, chunkResidency
  )

  private val world = testWorld()

  /** One tree per column. */
  private class TreeSource : WorldObjectSource {
    var asked = 0

    override val kinds = setOf(StaticEntityKind.TREE)

    override fun sitesIn(chunk: ChunkPos): List<WorldObjectSite> {
      asked++
      val tree = WorldObjectSite(
        kind = StaticEntityKind.TREE,
        propId = (chunk.x.toLong() shl 32) or (chunk.y.toLong() and 0xFFFFFFFFL),
        position = Vec3L(chunk.x * 32L + 20, chunk.y * 32L + 20, 64),
        variant = 0,
        heightDm = 80,
        yaw = 0f
      )
      return listOf(tree)
    }
  }

  private fun workbenchIn(chunkX: Int, chunkY: Int, structureId: Long): StructureEntry {
    val entry = StructureEntry(
      id = structureId,
      kind = StaticEntityKind.WORKBENCH,
      ownerMasterId = 1L,
      ownerAccountId = null,
      position = Vec3L(chunkX * 32L + 5, chunkY * 32L + 6, 64),
      yaw = 0f
    )
    stationsByColumn[chunkX to chunkY] = listOf(entry)
    return entry
  }

  private fun bestiaAt(position: Vec3L): EntityId {
    return world.createEntity { id ->
      world.add(id, Position.fromVec3(position))
      world.add(id, SimulationAnchor())
    }
  }

  private fun tick(budget: Int = 4) {
    anchors.update(world, 0.05f)
    residency.drain(world, budget)
  }

  private fun stationIn(chunkX: Int, chunkY: Int): EntityId? {
    return residency.entitiesIn(chunkX, chunkY).firstOrNull { world.has(it, PlayerStructureIdentity::class) }
  }

  @Test
  fun `a station near a bestia is in the world with no client nearby`() {
    workbenchIn(0, 0, structureId = 7L)
    bestiaAt(Vec3L(0, 0, 64))

    tick()

    val station = assertNotNull(stationIn(0, 0), "the station did not load for the bestia")
    assertEquals(7L, world.get(station, PlayerStructureIdentity::class)?.structureId)
    assertTrue(
      station in aoi.queryEntitiesInCube(Vec3L(5, 6, 64), 4, AoiLayer.STATIC_ONLY),
      "a roaming NPC cannot find the station"
    )
    assertEquals(0, trees.asked, "trees loaded for a bestia, which would cost too much around every anchor")
    assertTrue(batches.isEmpty(), "a batch went out with no client holding the ground")
  }

  @Test
  fun `a client walking up later is sent the station with the trees`() {
    workbenchIn(0, 0, structureId = 7L)
    bestiaAt(Vec3L(0, 0, 64))
    tick()
    val station = stationIn(0, 0)

    subscriptions.markSent(1L, ChunkPos(0, 0, 0))
    tick()

    val (audience, batch) = batches.single()
    assertEquals(setOf(1L), audience)
    assertEquals(
      setOf(StaticEntityKind.TREE, StaticEntityKind.WORKBENCH),
      batch.entries.map { it.kind }.toSet()
    )
    assertTrue(batch.entries.any { it.entityId == station }, "the client was sent a new entity for the same station")
  }

  @Test
  fun `a station leaves only when its last holder of any kind goes`() {
    workbenchIn(0, 0, structureId = 7L)
    subscriptions.markSent(1L, ChunkPos(0, 0, 0))
    val bestia = bestiaAt(Vec3L(0, 0, 64))
    tick()
    assertEquals(2, residency.entitiesIn(0, 0).size)

    subscriptions.unsend(1L, ChunkPos(0, 0, 0))
    tick()

    val station = stationIn(0, 0)
    assertEquals(listOf(station), residency.entitiesIn(0, 0).toList(), "the tree stayed after the client left")

    world.destroy(bestia)
    tick()

    assertEquals(0, residency.entitiesIn(0, 0).size, "the station stayed after every holder left")
    assertEquals(0, aoi.getTotalEntityCount())
  }

  /** The two layers drain from separate queues, so the station can land a drain after the trees' batch. */
  @Test
  fun `a station that loads after its column's batch is sent again`() {
    workbenchIn(0, 0, structureId = 7L)
    workbenchIn(1, 0, structureId = 8L)

    // Ahead of the client's column in the queue, so a budget of one loads this station first.
    chunkResidency.hold(ChunkPos(1, 0, 0))
    subscriptions.markSent(1L, ChunkPos(0, 0, 0))

    residency.drain(world, budget = 1)
    assertEquals(listOf(StaticEntityKind.TREE), batches.single().second.entries.map { it.kind })

    residency.drain(world, budget = 1)

    val (audience, again) = batches.last()
    assertEquals(setOf(1L), audience)
    assertEquals(2, again.entries.size, "the station never reached the client")
  }

  @Test
  fun `ground a bestia holds costs no budget unless a station stands on it`() {
    workbenchIn(0, 0, structureId = 7L)
    bestiaAt(Vec3L(0, 0, 64))

    // Two drains: the station's column spends the budget, and the empty columns queued behind it follow free.
    // At one column per drain, nine columns would take nine.
    tick(budget = 1)
    tick(budget = 1)

    assertEquals(0, residency.pending, "empty columns around the bestia waited for budget")
    assertNotNull(stationIn(0, 0))
    verify(exactly = 9) { structures.`in`(any(), any()) }
    verify(exactly = 0) { structures.all() }
  }

  @Test
  fun `a station built where only a bestia stands is announced to nobody`() {
    bestiaAt(Vec3L(0, 0, 64))
    tick()

    val entry = workbenchIn(0, 0, structureId = 7L)
    val station = residency.placeNow(world, stations.siteOf(entry))

    assertNotNull(station, "the column is held, so the station has to stand at once")
    assertEquals(0, residency.awaitingBatches, "a batch nobody waits for would never flush")
  }
}
