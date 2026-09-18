package net.bestia.zone.world.ground

import io.mockk.every
import io.mockk.mockk
import net.bestia.worldgen.voxel.BlockType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SurfaceTrampleableGroundTest {

  private val surface = mockk<SurfaceBlockLookup>()
  private val ground = SurfaceTrampleableGround(surface)

  private fun wearOn(block: BlockType?): Double {
    every { surface.blockAt(any(), any()) } returns block
    return ground.wearAt(0, 0)
  }

  private fun printOn(block: BlockType?): Double {
    every { surface.blockAt(any(), any()) } returns block
    return ground.impressionAt(0, 0)
  }

  @Test
  fun `meadow wears bare`() {
    assertTrue(wearOn(BlockType.GRASS) > 0.0)
  }

  @Test
  fun `a paved road never becomes a dirt track`() {
    // The case this table exists for: a road is already the path, and wearing it would be drawing a desire
    // line along the thing people desired.
    assertEquals(0.0, wearOn(BlockType.COBBLESTONE))
    assertEquals(0.0, wearOn(BlockType.MASONRY))
  }

  @Test
  fun `rock and water take no path`() {
    assertEquals(0.0, wearOn(BlockType.GRANITE))
    assertEquals(0.0, wearOn(BlockType.STONE))
    assertEquals(0.0, wearOn(BlockType.WATER))
  }

  @Test
  fun `loose ground is left to the disturbance layer`() {
    // Sand and snow take a print rather than wearing bare - no grass to kill, no earth to expose. Here they
    // would give a beach permanent brown paths.
    assertEquals(0.0, wearOn(BlockType.SAND))
    assertEquals(0.0, wearOn(BlockType.SNOW))
  }

  @Test
  fun `the ground that wears and the ground that holds a print are not the same ground`() {
    // The reason there are two maps rather than one number. Getting this backwards gives beaches permanent
    // brown paths and snowfields no tracks at all.
    assertEquals(0.0, wearOn(BlockType.SNOW))
    assertTrue(printOn(BlockType.SNOW) > 0.0)
  }

  @Test
  fun `snow and mud hold a track that a meadow only bends`() {
    assertTrue(printOn(BlockType.SNOW) > printOn(BlockType.GRASS))
    assertTrue(printOn(BlockType.MUD) > printOn(BlockType.GRASS))
  }

  @Test
  fun `stone and paving hold no tracks`() {
    assertEquals(0.0, printOn(BlockType.GRANITE))
    assertEquals(0.0, printOn(BlockType.COBBLESTONE))
    assertEquals(0.0, printOn(BlockType.MASONRY))
    assertEquals(0.0, printOn(BlockType.ICE))
    assertEquals(0.0, printOn(BlockType.WATER))
    assertEquals(0.0, printOn(null))
  }

  @Test
  fun `ground with no block at all is not walked on`() {
    assertEquals(0.0, wearOn(null))
  }

  @Test
  fun `living grass gives way faster than what a drought left of it`() {
    assertTrue(wearOn(BlockType.GRASS) > wearOn(BlockType.DRY_GRASS))
  }
}
