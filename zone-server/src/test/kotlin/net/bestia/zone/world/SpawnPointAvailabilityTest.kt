package net.bestia.zone.world

import io.mockk.every
import io.mockk.mockk
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.derived.VoxelEdit
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.voxel.BlockType
import net.bestia.worldgen.voxel.Occupancy
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.world.persistence.MasterSpawnPoint
import net.bestia.zone.world.settlement.SettlementFates
import net.bestia.zone.world.stream.ChunkCoords
import net.bestia.zone.world.stream.ChunkService
import net.bestia.zone.world.stream.ChunkStreamConfig
import net.bestia.zone.world.stream.DryLand
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SpawnPointAvailabilityTest {

  private val chunkService = ChunkService(
    mockk<WorldService> {
      every { generated } returns world
      every { config } returns world.config
      every { isLoaded } returns true
    },
    ChunkStreamConfig()
  )

  private val fallen = HashSet<Int>()
  private val fates = mockk<SettlementFates> { every { hasFallen(any()) } answers { firstArg<Int>() in fallen } }

  private val sut = SpawnPointAvailability(chunkService, fates)

  private val land = DryLand.find(world, chunkService)
  private val ground = Vec3L(land.voxelX, land.voxelY, land.surfaceZ.toLong())

  /** Five homes, all on the same dry ground; only the order and the fates tell them apart. */
  private val points = (0 until 5).map { rank ->
    MasterSpawnPoint(rank, "Town $rank", "village", 100, ground, rank)
  }

  private fun offeredNames(): List<String> {
    return sut.offered().map { it.settlementName }
  }

  @Test
  fun `the first homes by rank are offered`() {
    sut.load(points)
    sut.refresh()

    assertEquals(listOf("Town 0", "Town 1", "Town 2"), offeredNames())
  }

  @Test
  fun `a fallen town makes way for the next one`() {
    sut.load(points)
    fallen.add(1)

    sut.refresh()

    assertEquals(listOf("Town 0", "Town 2", "Town 3"), offeredNames())
  }

  @Test
  fun `a flooded home is not offered`() {
    val wet = flood(ground.x + 3, ground.y)
    sut.load(listOf(points[0].copyAt(wet)) + points.drop(1))

    sut.refresh()

    assertEquals(listOf("Town 1", "Town 2", "Town 3"), offeredNames())
  }

  @Test
  fun `with every town fallen there is still one dry place to start`() {
    sut.load(points)
    fallen.addAll(0 until 5)

    sut.refresh()

    val offers = sut.offered()
    assertEquals(1, offers.size)
    assertEquals("Town 0", offers.single().settlementName)
  }

  private fun MasterSpawnPoint.copyAt(position: Vec3L): MasterSpawnPoint {
    return MasterSpawnPoint(settlementIndex, settlementName, tier, population, position, rank)
  }

  /** Puts water on the ground at ([x], [y]) and returns that ground. */
  private fun flood(x: Long, y: Long): Vec3L {
    val groundZ = config.voxelZOf(chunkService.surfaceElevationAt(x, y)!!).toLong()
    val above = ChunkCoords.localise(config, x, y, groundZ + 1)!!
    val index = ChunkCoords.voxelIndex(config, above.localX, above.localY, above.localZ)
    val water = VoxelEdit.pack(index, BlockType.WATER, Occupancy.FULL)

    assertNotEquals(0, chunkService.editFluid(above.chunk, longArrayOf(water)), "the test could not put water there")
    return Vec3L(x, y, groundZ)
  }

  private companion object {
    const val SEED = 9001L
    const val WORLD_CELLS = 48

    // In the companion so JUnit's per-method instances share one generated world. Read-only here.
    val world = StandardWorld.build(WorldConfig(seed = SEED, widthCells = WORLD_CELLS, heightCells = WORLD_CELLS))
    val config = world.config
  }
}
