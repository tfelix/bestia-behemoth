package net.bestia.zone.ai.ecs

import io.mockk.every
import io.mockk.mockk
import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.WorldConfig
import net.bestia.zone.ai.core.action.CompositeActionResolver
import net.bestia.zone.ai.core.state.Blackboard
import net.bestia.zone.ai.domain.bestia.BestiaDomain
import net.bestia.zone.ai.domain.bestia.WorkArea
import net.bestia.zone.aoi.EntityAOIService
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.message.ChunkFanOut
import net.bestia.zone.message.SMSG
import net.bestia.zone.prop.PropKindRegistry
import net.bestia.zone.prop.WorldObjectDivergenceRegistry
import net.bestia.zone.prop.WorldObjectResidencyService
import net.bestia.zone.prop.WorldObjectSite
import net.bestia.zone.prop.WorldObjectSource
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.residency.ChunkResidency
import net.bestia.zone.world.stream.ChunkSubscriptionService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** A gatherer keeps the trees of its own area in the world, and only those, with no client anywhere. */
class GatherAreaResidencyTest {

  private val chunkResidency = ChunkResidency()
  private val subscriptions = ChunkSubscriptionService(chunkResidency)

  private val worldService: WorldService = mockk {
    every { config } returns WorldConfig(seed = 1L, chunkSize = 32, voxelSize = 1.0)
    every { record } returns mockk { every { pipelineVersion } returns 1L }
  }

  private val divergence: WorldObjectDivergenceRegistry = mockk {
    every { of(any()) } returns null
  }

  private val fanOut = object : ChunkFanOut {
    override fun fanOut(accountIds: Collection<Long>, message: SMSG): Int = accountIds.size
  }

  private val residency = WorldObjectResidencyService(
    listOf(TreeSource()), PropKindRegistry().also { it.load() }, EntityAOIService(), fanOut, worldService,
    divergence, subscriptions, chunkResidency, mockk(relaxed = true)
  )

  private val system = GatherAreaResidencySystem(residency)

  private val world = testWorld()

  /** One tree per column. */
  private class TreeSource : WorldObjectSource {
    override val kinds = setOf(StaticEntityKind.TREE)

    override fun sitesIn(chunk: ChunkPos): List<WorldObjectSite> {
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

  private fun bestia(area: WorkArea?): EntityId {
    val memory = Blackboard()
    area?.let { memory.set(BestiaDomain.GATHER_AREA, it, Blackboard.PERMANENT) }

    return world.createEntity { id ->
      add(id, OwnedBestia(masterId = 1L, playerBestiaId = id))
      add(id, AiAgent("test", "test", emptyList(), CompositeActionResolver(emptyList()), memory))
    }
  }

  private fun tick() {
    system.update(world, 1f)
    residency.drain(world, budget = 16)
  }

  @Test
  fun `a gatherer loads the columns its area touches`() {
    // 20 around the middle of column (1, 1) reaches its four neighbours and none of its corners.
    bestia(WorkArea(Vec3L(48, 48, 64), radius = 20))

    tick()

    assertEquals(5, residency.residentColumns)
    assertEquals(5, residency.residentEntities)
  }

  @Test
  fun `a bestia under other orders loads nothing`() {
    bestia(area = null)

    tick()

    assertEquals(0, residency.residentColumns)
  }

  @Test
  fun `the area leaves with the gatherer`() {
    val gatherer = bestia(WorkArea(Vec3L(48, 48, 64), radius = 20))
    tick()

    world.destroy(gatherer)
    tick()

    assertEquals(0, residency.residentColumns)
  }

  @Test
  fun `a new home moves the area and keeps what both share`() {
    val gatherer = bestia(WorkArea(Vec3L(48, 48, 64), radius = 20))
    tick()
    val sharedTree = residency.entitiesIn(2, 1).single()

    world.getOrThrow(gatherer, AiAgent::class).memory
      .set(BestiaDomain.GATHER_AREA, WorkArea(Vec3L(80, 48, 64), radius = 20), Blackboard.PERMANENT)
    tick()

    assertEquals(5, residency.residentColumns)
    assertEquals(0, residency.entitiesIn(0, 1).size, "the column only the old area touched is still loaded")
    assertEquals(sharedTree, residency.entitiesIn(2, 1).single(), "a column both areas touch was loaded again")
  }

  @Test
  fun `a client leaving does not take a gatherer's column with it`() {
    bestia(WorkArea(Vec3L(48, 48, 64), radius = 4))
    subscriptions.markSent(1L, ChunkPos(1, 1, 0))
    tick()

    subscriptions.unsend(1L, ChunkPos(1, 1, 0))
    tick()

    assertEquals(1, residency.residentColumns)
  }
}
