package net.bestia.worldgen.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorldWrapTest {

  /** 100 km across at kilometre cells, wrapping east to west only. */
  private val config = WorldConfig(
    seed = 1L,
    widthCells = 100,
    heightCells = 100,
    chunkSize = 32,
    voxelSize = 1.0,
    oceanBorderOverride = 6_000.0,
    wrapX = true
  )

  private val wrap = WorldWrap(config)

  @Test
  fun `stepping west off zero arrives at the far edge rather than outside the world`() {
    // The reason this is not `x % width`: the remainder of a negative is negative, so a player who walks one
    // metre west of the origin ends up at -1 - outside every raster, off every chunk index, and reported to
    // other players as being a world away.
    assertEquals(99_999.0, wrap.normaliseX(-1.0), 1e-9)
    assertEquals(1.0, wrap.normaliseX(100_001.0), 1e-9)
    assertEquals(50_000.0, wrap.normaliseX(50_000.0), 1e-9)

    // Many laps round, in either direction.
    assertEquals(7.0, wrap.normaliseX(7.0 + 100_000.0 * 12), 1e-6)
    assertEquals(7.0, wrap.normaliseX(7.0 - 100_000.0 * 12), 1e-6)
  }

  @Test
  fun `an unwrapped axis is left alone`() {
    // North to south does not wrap, so a position off the top is out of the world and saying so is the answer.
    assertEquals(-5.0, wrap.normaliseY(-5.0), 1e-9)
    assertEquals(120_000.0, wrap.normaliseY(120_000.0), 1e-9)
  }

  @Test
  fun `two points either side of the seam are neighbours, not a world apart`() {
    // The failure this prevents is not cosmetic. Interest management asks "who is within range", pathfinding
    // asks "how far", and a naive subtraction answers 99 998 m for two players ten metres apart across the
    // seam - so they cannot see each other, and an NPC between them walks the long way round the world.
    assertEquals(20.0, wrap.distance(99_990.0, 0.0, 10.0, 0.0), 1e-9)
    assertEquals(20.0, wrap.deltaX(99_990.0, 10.0), 1e-9)

    // Signed, so it says which way as well as how far: eastwards is positive here.
    assertEquals(-20.0, wrap.deltaX(10.0, 99_990.0), 1e-9)
    assertTrue(wrap.isWithin(50.0, 99_990.0, 0.0, 10.0, 0.0))
    assertFalse(wrap.isWithin(10.0, 99_990.0, 0.0, 10.0, 0.0))
  }

  @Test
  fun `the long way round is never chosen`() {
    // Anything more than half the world apart is closer the other way.
    assertEquals(-40_000.0, wrap.deltaX(10_000.0, 70_000.0), 1e-9)
    assertEquals(40_000.0, wrap.deltaX(70_000.0, 10_000.0), 1e-9)

    // Exactly antipodal is a tie; it resolves the same way every time rather than on how the floats fell.
    assertEquals(50_000.0, wrap.deltaX(0.0, 50_000.0), 1e-9)
    assertEquals(50_000.0, wrap.deltaX(50_000.0, 0.0), 1e-9)
  }

  @Test
  fun `distance is unaffected on the unwrapped axis`() {
    assertEquals(90_000.0, wrap.deltaY(5_000.0, 95_000.0), 1e-9)
  }

  @Test
  fun `a chunk request past the eastern edge resolves to the westernmost chunk`() {
    // What a client request goes through before it reaches the generator. Without it, asking for the chunk one
    // past the edge samples off the end of the raster and returns a column of air off the side of the world.
    val across = wrap.chunksAcross
    assertEquals(3125, across, "100 km of 32 m chunks")

    assertEquals(ChunkPos(0, 5, 2), wrap.normalise(ChunkPos(across, 5, 2)))
    assertEquals(ChunkPos(across - 1, 5, 2), wrap.normalise(ChunkPos(-1, 5, 2)))
    // The vertical coordinate is never wrapped: up is not a loop.
    assertEquals(-3, wrap.normalise(ChunkPos(across, 5, -3)).z)
    // An unwrapped axis passes through, out of range and all.
    assertEquals(9999, wrap.normalise(ChunkPos(0, 9999, 0)).y)
  }

  @Test
  fun `the ocean margin is recognised on all four edges`() {
    // Ocean on all four, even though only east and west wrap: the unwrapped edges still need to be somewhere a
    // player cannot walk off, and cold polar sea is a better answer than an invisible wall.
    assertTrue(wrap.isInOceanBorder(100.0, 50_000.0), "west")
    assertTrue(wrap.isInOceanBorder(99_900.0, 50_000.0), "east")
    assertTrue(wrap.isInOceanBorder(50_000.0, 100.0), "south")
    assertTrue(wrap.isInOceanBorder(50_000.0, 99_900.0), "north")

    assertFalse(wrap.isInOceanBorder(50_000.0, 50_000.0), "the middle is not the margin")
    assertFalse(wrap.isInOceanBorder(6_001.0, 50_000.0), "just inside the margin's inner boundary")
  }

  @Test
  fun `a world without wrapping behaves as an ordinary bounded one`() {
    val plain = WorldWrap(config.copy(wrapX = false, oceanBorderOverride = 0.0))

    assertEquals(-1.0, plain.normaliseX(-1.0), 1e-9)
    assertEquals(99_980.0, plain.deltaX(10.0, 99_990.0), 1e-9)
    assertEquals(ChunkPos(-1, 5, 0), plain.normalise(ChunkPos(-1, 5, 0)))
    assertFalse(plain.isInOceanBorder(1.0, 1.0))
  }
}
