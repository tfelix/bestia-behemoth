package net.bestia.zone.structure

import io.mockk.every
import io.mockk.mockk
import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.derived.AgentProfile
import net.bestia.worldgen.derived.DerivedStore
import net.bestia.worldgen.voxel.BlockType
import net.bestia.worldgen.voxel.VoxelChunk
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.navigation.NavigationConfig
import net.bestia.zone.navigation.local.LocalPathfindingService
import net.bestia.zone.world.stream.ChunkService
import net.bestia.zone.world.stream.ChunkWalkQuery
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Placed pieces, rasterized from the real catalogue, as the walk query and the local A* search see them. */
class StructureWalkTest {

  private val world = WorldConfig(seed = 1L)
  private val catalogue = StructurePieceCatalogue().apply { load() }
  private val index = StructureCellIndex(world) { it }
  private var nextPieceId = 1L

  /** Flat sand stood on at 1. */
  private val ground = VoxelChunk(ChunkPos(0, 0, 0), world.chunkSize, world.chunkHeight).apply {
    for (y in 0 until world.chunkSize) {
      for (x in 0 until world.chunkSize) this[x, y, 0] = BlockType.SAND
    }
  }

  private fun place(kind: StaticEntityKind, origin: Vec3L, quarterTurns: Int = 0) {
    index.put(nextPieceId++, StructureRasterizer.rasterize(catalogue.of(kind), origin, quarterTurns))
  }

  private fun query(): ChunkWalkQuery {
    val derived = DerivedStore(
      voxels = { if (it == ground.chunk) ground else VoxelChunk(it, world.chunkSize, world.chunkHeight) },
      agent = AgentProfile.forMaxSlope(world.voxelSize, 60.0),
      structures = index::cellsOf
    )
    derived.track(ground.chunk)
    derived.rebuildAll()

    val service = mockk<ChunkService> {
      every { isReady } returns true
      every { config } returns world
      every { derived() } returns derived
      every { normalise(any()) } answers { firstArg() }
    }

    return ChunkWalkQuery(service)
  }

  @Test
  fun `a wall refuses the step through it and leaves the ground beside it open`() {
    place(StaticEntityKind.STRUCTURE_WALL, Vec3L(5, 0, 1))
    val query = query()

    assertFalse(query.canStep(Vec3L(5, 0, 1), Vec3L(6, 0, 1)))
    assertFalse(query.canStep(Vec3L(6, 1, 1), Vec3L(5, 1, 1)), "from the other side")
    assertTrue(query.canStep(Vec3L(5, 2, 1), Vec3L(6, 2, 1)), "past its end")
  }

  @Test
  fun `a turned wall stands on the turned border`() {
    place(StaticEntityKind.STRUCTURE_WALL, Vec3L(5, 5, 1), quarterTurns = 1)
    val query = query()

    assertFalse(query.canStep(Vec3L(5, 5, 1), Vec3L(5, 4, 1)))
    assertTrue(query.canStep(Vec3L(5, 5, 1), Vec3L(6, 5, 1)))
  }

  @Test
  fun `a search goes round a wall`() {
    place(StaticEntityKind.STRUCTURE_WALL, Vec3L(5, 0, 1))
    val search = LocalPathfindingService(query(), NavigationConfig())

    val path = assertNotNull(search.path(Vec3L(3, 0, 1), Vec3L(8, 0, 1)))

    assertEquals(Vec3L(8, 0, 1), path.last())
    assertTrue(path.none { it.y in 0L..1L && it.x == 6L }, "walked through the wall: $path")
  }

  @Test
  fun `stairs lead up onto a floor three metres above the ground`() {
    place(StaticEntityKind.STRUCTURE_STAIRS, Vec3L(10, 5, 1))
    place(StaticEntityKind.STRUCTURE_FLOOR, Vec3L(16, 5, 4))
    val query = query()

    val upper = assertNotNull(query.surfaceAt(Vec3L(17, 6, 5)))
    assertEquals(5L, upper, "the floor stands at 4.5, rounded")
    assertEquals(1L, query.surfaceAt(Vec3L(17, 6, 1)), "with the ground still walkable under it")

    val search = LocalPathfindingService(query, NavigationConfig())
    val path = assertNotNull(search.path(Vec3L(9, 5, 1), Vec3L(17, 6, upper)))

    assertEquals(Vec3L(17, 6, upper), path.last())
    assertTrue(path.any { it.x == 15L && it.y == 5L }, "went up the stairs: $path")
  }

  @Test
  fun `the floor cannot be stepped onto from the ground under it`() {
    place(StaticEntityKind.STRUCTURE_FLOOR, Vec3L(16, 5, 1))

    assertTrue(query().canStep(Vec3L(15, 5, 1), Vec3L(16, 5, 2)), "a floor on the ground is a half step up")

    val raised = StructureWalkTest()
    raised.place(StaticEntityKind.STRUCTURE_FLOOR, Vec3L(16, 5, 2))
    assertFalse(raised.query().canStep(Vec3L(15, 5, 1), Vec3L(16, 5, 3)), "one and a half metres is no step")
  }
}
