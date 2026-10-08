package net.bestia.zone.water

import io.mockk.every
import io.mockk.mockk
import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.derived.VoxelEdit
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.voxel.BlockType
import net.bestia.worldgen.voxel.CarveBrush
import net.bestia.worldgen.voxel.Occupancy
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.stream.ChunkCoords
import net.bestia.zone.world.stream.ChunkService
import net.bestia.zone.world.stream.ChunkStreamConfig
import net.bestia.zone.world.stream.DryLand
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/** Digging through the wall beside water lets the water in: the river trench, played against the sea. */
class WaterBreachTest {

  private fun chunkService(allowWaterBreach: Boolean): ChunkService {
    return ChunkService(
      mockk<WorldService> {
        every { generated } returns world
        every { config } returns world.config
        every { isLoaded } returns true
      },
      ChunkStreamConfig(allowWaterBreach = allowWaterBreach)
    )
  }

  private fun blockAt(chunkService: ChunkService, voxel: Vec3L): Byte {
    val localised = ChunkCoords.localise(config, voxel.x, voxel.y, voxel.z)!!
    val index = ChunkCoords.voxelIndex(config, localised.localX, localised.localY, localised.localZ)
    return chunkService.merged(chunkService.normalise(localised.chunk)).blocks[index]
  }

  /** A ground voxel just under sea level with the sea on one face, walking from the centre toward the edge. */
  private fun findShore(chunkService: ChunkService): Vec3L {
    val size = config.chunkSize
    val centre = (config.widthMetres / config.chunkExtent).toInt() / 2
    val sides = listOf(1L to 0L, -1L to 0L, 0L to 1L, 0L to -1L)

    for (chunkXY in centre downTo 0) {
      val heights = world.columns.heights(ChunkPos(chunkXY, chunkXY, 0), 0)
      for (localY in 0 until size) {
        for (localX in 0 until size) {
          // On a gentle beach the bank is the last column whose voxel just under sea level is still ground.
          if (heights[localX, localY] !in -0.5..1.0) continue

          val land = Vec3L(chunkXY.toLong() * size + localX, chunkXY.toLong() * size + localY, -1)
          val ground = BlockType.of(blockAt(chunkService, land).toInt() and 0xFF)
          if (!ground.solid || !ground.carvable) continue

          if (sides.any { (dx, dy) -> blockAt(chunkService, Vec3L(land.x + dx, land.y + dy, land.z)) == WATER }) {
            return land
          }
        }
      }
    }

    error("no shore found in the fixture world")
  }

  private fun settle(water: WaterService) {
    repeat(20_000) {
      if (water.isIdle) return
      water.step(0.1f)
    }
    error("the water never settled")
  }

  @Test
  fun `a hole dug beside the sea fills with sea water`() {
    val chunkService = chunkService(allowWaterBreach = true)
    val water = WaterService(chunkService, WaterConfig())
    val shore = findShore(chunkService)

    val carved = chunkService.carve(CarveBrush.sphere(shore.x + 0.5, shore.y + 0.5, shore.z + 0.5, RADIUS))
    settle(water)

    val opened = carved.voxels.filter { it.remainingOccupancy == 0 }
    assertTrue(opened.isNotEmpty(), "the carve opened nothing")
    assertTrue(
      opened.any { blockAt(chunkService, Vec3L(it.voxelX, it.voxelY, it.voxelZ.toLong())) == WATER },
      "the sea did not run into the hole"
    )
  }

  /** `CarveRules` sees only one chunk's faces, so the wall here is built inside one: water poured onto dry land. */
  @Test
  fun `the ground under water stays where water may not breach`() {
    val chunkService = chunkService(allowWaterBreach = false)
    val land = DryLand.find(world, chunkService)
    val above = ChunkCoords.localise(config, land.voxelX, land.voxelY, land.surfaceZ + 1L)!!
    val aboveIndex = ChunkCoords.voxelIndex(config, above.localX, above.localY, above.localZ)
    chunkService.editFluid(above.chunk, longArrayOf(VoxelEdit.pack(aboveIndex, BlockType.WATER, Occupancy.FULL)))

    chunkService.carve(CarveBrush.sphere(land.voxelX + 0.5, land.voxelY + 0.5, land.surfaceZ + 0.5, RADIUS))

    val ground = blockAt(chunkService, Vec3L(land.voxelX, land.voxelY, land.surfaceZ.toLong()))
    assertTrue(BlockType.of(ground.toInt() and 0xFF).solid, "the ground under the water was dug away")
  }

  private companion object {
    const val SEED = 9001L
    const val WORLD_CELLS = 48
    const val RADIUS = CarveBrush.MIN_RADIUS + 0.4
    val WATER = BlockType.WATER.id.toByte()

    // In the companion so JUnit's per-method instances share one generated world. Read-only here.
    val world = StandardWorld.build(WorldConfig(seed = SEED, widthCells = WORLD_CELLS, heightCells = WORLD_CELLS))
    val config = world.config
  }
}
