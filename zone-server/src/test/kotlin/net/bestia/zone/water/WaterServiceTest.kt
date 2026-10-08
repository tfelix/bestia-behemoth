package net.bestia.zone.water

import io.mockk.every
import io.mockk.mockk
import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.voxel.BlockType
import net.bestia.worldgen.voxel.Occupancy
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.stream.ChunkCoords
import net.bestia.zone.world.stream.ChunkService
import net.bestia.zone.world.stream.ChunkStreamConfig
import net.bestia.zone.world.stream.DryLand
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WaterServiceTest {

  private val chunkService = ChunkService(
    mockk<WorldService> {
      every { generated } returns world
      every { config } returns world.config
      every { isLoaded } returns true
    },
    ChunkStreamConfig()
  )

  private val service = WaterService(chunkService, WaterConfig())

  private val land = DryLand.find(world, chunkService)

  private val home: ChunkPos = ChunkCoords.localise(config, land.voxelX, land.voxelY, land.surfaceZ.toLong())!!.chunk

  /** Water placed since generation, summed over the chunks around [home]: what a pour adds and nothing else. */
  private fun placedWater(): Long {
    var total = 0L
    for (dz in -1..1) {
      for (dy in -1..1) {
        for (dx in -1..1) {
          val pos = chunkService.normalise(ChunkPos(home.x + dx, home.y + dy, home.z + dz))
          val merged = chunkService.merged(pos)
          val base = chunkService.base(pos)
          for (index in 0 until merged.volume) {
            if (merged.blocks[index] == WATER && base.blocks[index] != WATER) {
              total += Occupancy.unsigned(merged.occupancy[index])
            }
          }
        }
      }
    }
    return total
  }

  /** Steps until the service lets go of everything, and fails a test that never settles. */
  private fun settle() {
    repeat(20_000) {
      if (service.isIdle) return
      service.step(STEP_SECONDS)
    }
    error("the water never settled")
  }

  @Test
  fun `poured water falls, spreads over the ground, and is committed once it settles`() {
    service.requestPour(land.voxelX, land.voxelY, land.surfaceZ + 6L, radius = 2)
    val poured = service.drainPours().toLong() * Occupancy.FULL

    settle()

    assertTrue(poured > 0, "the pour found no air")
    assertEquals(poured, placedWater(), "water was created or lost on the way down")
    assertEquals(0, service.heldChunks, "a settled flood is let go of")

    val top = ChunkCoords.localise(config, land.voxelX, land.voxelY, land.surfaceZ + 6L)!!
    val atPourHeight = chunkService.merged(top.chunk).blocks[ChunkCoords.voxelIndex(config, top.localX, top.localY, top.localZ)]
    assertEquals(BlockType.AIR.id.toByte(), atPourHeight, "the poured water is still hanging in the air")
  }

  @Test
  fun `water poured onto moving water joins it`() {
    service.requestPour(land.voxelX, land.voxelY, land.surfaceZ + 6L, radius = 2)
    var poured = service.drainPours().toLong() * Occupancy.FULL
    repeat(5) { service.step(STEP_SECONDS) }

    service.requestPour(land.voxelX, land.voxelY, land.surfaceZ + 6L, radius = 2)
    poured += service.drainPours().toLong() * Occupancy.FULL
    settle()

    assertEquals(poured, placedWater())
  }

  @Test
  fun `committing everything leaves no water behind in the simulation`() {
    service.requestPour(land.voxelX, land.voxelY, land.surfaceZ + 6L, radius = 2)
    val poured = service.drainPours().toLong() * Occupancy.FULL
    repeat(3) { service.step(STEP_SECONDS) }
    assertTrue(service.heldChunks > 0, "the water settled before the test could catch it moving")

    service.commitAll()

    assertEquals(poured, placedWater())
  }

  @Test
  fun `a pour past the cap is refused`() {
    assertFailsWith<IllegalArgumentException> {
      service.requestPour(0, 0, 0, radius = WaterService.MAX_POUR_RADIUS + 1)
    }
  }

  private companion object {
    const val SEED = 9001L
    const val WORLD_CELLS = 48
    const val STEP_SECONDS = 0.1f
    val WATER = BlockType.WATER.id.toByte()

    // In the companion so JUnit's per-method instances share one generated world. Read-only here.
    val world = StandardWorld.build(WorldConfig(seed = SEED, widthCells = WORLD_CELLS, heightCells = WORLD_CELLS))
    val config = world.config
  }
}
