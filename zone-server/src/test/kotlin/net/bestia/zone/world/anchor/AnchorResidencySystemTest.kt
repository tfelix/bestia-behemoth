package net.bestia.zone.world.anchor

import io.mockk.every
import io.mockk.mockk
import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.WorldConfig
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.ActivePlayer
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.residency.ChunkResidency
import net.bestia.zone.world.stream.ChunkService
import net.bestia.zone.world.stream.ChunkStreamConfig
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnchorResidencySystemTest {

  private val chunkService: ChunkService = mockk {
    every { isReady } returns true
    every { config } returns WorldConfig(seed = 1L, chunkSize = 32, voxelSize = 1.0)
    every { slabEpoch } returns 0L
    every { cachedSlabsOf(any()) } returns intArrayOf(0)
    every { normalise(any()) } answers { firstArg() }
  }
  private val residency = ChunkResidency()
  private val index = SimulationAnchorIndex()

  private fun systemWith(maxHoldingAnchors: Int = 16) = AnchorResidencySystem(
    chunkService,
    ChunkStreamConfig(viewRadiusChunks = 1, releaseMarginChunks = 1),
    SimulationAnchorConfig(maxHoldingAnchors = maxHoldingAnchors),
    residency,
    index,
  )

  private val system = systemWith()
  private val world = testWorld()

  private fun anchorAt(position: Vec3L): EntityId {
    return world.createEntity { id ->
      world.add(id, Position.fromVec3(position))
      world.add(id, SimulationAnchor())
    }
  }

  private fun tick(sut: AnchorResidencySystem = system) {
    sut.update(world, 0.05f)
  }

  @Test
  fun `a lone anchor holds the view volume around it`() {
    anchorAt(Vec3L(0, 0, 0))

    tick()

    assertEquals(9, residency.residentColumns, "a radius of one is three by three")
    assertTrue(residency.isResident(ChunkPos(-1, 1, 0)))
    assertEquals(1, index.size)
  }

  @Test
  fun `overlapping anchors keep shared ground until the last one goes`() {
    val first = anchorAt(Vec3L(0, 0, 0))
    val second = anchorAt(Vec3L(0, 0, 0))
    tick()

    world.destroy(first)
    tick()

    assertEquals(9, residency.residentColumns)

    world.destroy(second)
    tick()

    assertEquals(0, residency.residentChunks)
    assertEquals(0, index.size)
  }

  @Test
  fun `walking on keeps the margin behind and releases past it`() {
    val anchor = anchorAt(Vec3L(0, 0, 0))
    tick()

    moveTo(anchor, Vec3L(32, 0, 0))
    tick()

    assertTrue(residency.isResident(ChunkPos(-1, 0, 0)), "one chunk behind is inside the margin")
    assertTrue(residency.isResident(ChunkPos(2, 0, 0)), "the new edge is held")

    moveTo(anchor, Vec3L(96, 0, 0))
    tick()

    assertFalse(residency.isResident(ChunkPos(-1, 0, 0)), "three chunks behind is past it")
  }

  @Test
  fun `over the cap a player's anchor wins over a resting bestia`() {
    val resting = anchorAt(Vec3L(10_000, 0, 0))
    val player = anchorAt(Vec3L(0, 0, 0))
    world.add(player, ActivePlayer)

    tick(systemWith(maxHoldingAnchors = 1))

    assertEquals(9, residency.residentColumns, "only one view volume is held")
    assertTrue(index.anyWithinHorizontal(0, 0, 1))
    assertFalse(index.anyWithinHorizontal(10_000, 0, 1), "resting anchor $resting holds nothing")
  }

  private fun moveTo(id: EntityId, to: Vec3L) {
    world.getOrThrow(id, Position::class).apply {
      x = to.x
      y = to.y
      z = to.z
    }
  }
}
