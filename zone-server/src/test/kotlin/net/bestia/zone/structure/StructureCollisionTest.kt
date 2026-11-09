package net.bestia.zone.structure

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.voxel.BlockType
import net.bestia.worldgen.voxel.VoxelChunk
import net.bestia.zone.aoi.EntityAOIService
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.world.stream.ChunkService
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StructureCollisionTest {

  private val world = WorldConfig(seed = 1L)
  private val origin = ChunkPos(0, 0, 0)

  /** Flat sand in voxel 0, so a piece on the ground is placed at z 1. */
  private val ground = VoxelChunk(origin, world.chunkSize, world.chunkHeight).apply {
    for (y in 0 until world.chunkSize) {
      for (x in 0 until world.chunkSize) this[x, y, 0] = BlockType.SAND
    }
  }

  private val chunks = mockk<ChunkService> {
    every { config } returns world
    every { normalise(any()) } answers { firstArg() }
    every { merged(any()) } answers { if (firstArg<ChunkPos>() == origin) ground else VoxelChunk(firstArg(), world.chunkSize, world.chunkHeight) }
    every { structuresChanged(any()) } just runs
    every { useStructures(any()) } just runs
  }
  private val aoi = EntityAOIService()
  private val collision = StructureCollision(chunks, StructurePieceCatalogue().apply { load() }, aoi)

  private val floor = StaticEntityKind.STRUCTURE_FLOOR

  @Test
  fun `a floor fits on open ground`() {
    assertTrue(collision.fits(floor, Vec3L(4, 4, 1), 0f))
  }

  @Test
  fun `a floor does not fit into the ground`() {
    assertFalse(collision.fits(floor, Vec3L(4, 4, 0), 0f))
  }

  @Test
  fun `a floor does not fit onto another one`() {
    collision.stand(1, floor, Vec3L(4, 4, 1), 0f)

    assertFalse(collision.fits(floor, Vec3L(5, 5, 1), 0f), "overlaps one cell")
    assertTrue(collision.fits(floor, Vec3L(6, 4, 1), 0f), "right beside it")
  }

  @Test
  fun `a floor does not fit where somebody stands`() {
    aoi.setEntityPosition(99, Vec3L(5, 4, 1))

    assertFalse(collision.fits(floor, Vec3L(4, 4, 1), 0f))
  }

  @Test
  fun `a wall fits where somebody stands, since it is on a border`() {
    aoi.setEntityPosition(99, Vec3L(4, 4, 1))

    assertTrue(collision.fits(StaticEntityKind.STRUCTURE_WALL, Vec3L(4, 4, 1), 0f))
  }

  @Test
  fun `a floor in the air does not fit`() {
    assertFalse(collision.fits(floor, Vec3L(4, 4, 5), 0f))
  }

  @Test
  fun `a floor on top of a wall fits, and carries three more floors out beside it`() {
    collision.stand(1, StaticEntityKind.STRUCTURE_WALL, Vec3L(4, 4, 1), 0f)
    assertTrue(collision.fits(floor, Vec3L(4, 4, 4), 0f), "on the wall")
    collision.stand(2, floor, Vec3L(4, 4, 4), 0f)

    for ((pieceId, x) in listOf(3L to 6L, 4L to 8L, 5L to 10L)) {
      assertTrue(collision.fits(floor, Vec3L(x, 4, 4), 0f), "floor at x $x")
      collision.stand(pieceId, floor, Vec3L(x, 4, 4), 0f)
    }

    assertFalse(collision.fits(floor, Vec3L(12, 4, 4), 0f), "a fourth is too far from the wall")
  }

  @Test
  fun `standing and falling rebuild the walk tables of the chunk`() {
    collision.stand(1, floor, Vec3L(4, 4, 1), 0f)
    collision.fall(1)

    verify(exactly = 2) { chunks.structuresChanged(setOf(origin)) }
  }
}
