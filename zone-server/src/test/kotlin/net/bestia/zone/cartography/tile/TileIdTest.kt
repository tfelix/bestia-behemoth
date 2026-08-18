package net.bestia.zone.cartography.tile

import net.bestia.worldgen.vector.Aabb
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TileIdTest {

  @Test
  fun `level is metres per pixel as a power of two, independent of world size`() {
    assertEquals(1.0, TileId(0, 0, 0).metresPerPixel)
    assertEquals(64.0, TileId(6, 0, 0).metresPerPixel)
    assertEquals(512.0, TileId(9, 0, 0).metresPerPixel)

    // The whole point of anchoring at the fine end: a level means the same scale in every world.
    assertEquals(TileId(6, 0, 0).span, TileId(6, 999, -999).span)
  }

  @Test
  fun `a tile spans its own bounds exactly`() {
    val tile = TileId(5, 3, -2)
    val span = TileId.TILE_PIXELS * 32.0

    assertEquals(Aabb(3 * span, -2 * span, 4 * span, -1 * span), tile.bounds)
  }

  @Test
  fun `the viewport of a tile starts exactly on the tile boundary`() {
    // Parchment and the hatch lattice key off round(minX / metresPerPixel), so an off-by-a-fraction origin
    // here would put a seam down every tile edge. Exact equality is the requirement, not near equality.
    for (level in intArrayOf(0, 3, 6, 9)) {
      for (tx in longArrayOf(-5, 0, 1, 7777)) {
        val tile = TileId(level, tx, tx + 1)
        val view = tile.viewport()

        assertEquals(tile.bounds.minX, view.minX, "L$level tx=$tx minX")
        assertEquals(tile.bounds.minY, view.minY, "L$level tx=$tx minY")
        assertEquals(TileId.TILE_PIXELS, view.widthPx)
        assertEquals(tile.metresPerPixel, view.metresPerPixel)
      }
    }
  }

  @Test
  fun `a world position maps into the tile whose bounds contain it`() {
    for (level in 0..9) {
      for (x in doubleArrayOf(0.0, 1.0, 12_345.6, 127_999.9)) {
        val tile = TileId.of(level, x, x / 2)
        assertTrue(
          tile.bounds.contains(x, x / 2),
          "L$level: $x, ${x / 2} is not inside ${tile.bounds} of $tile"
        )
      }
    }
  }

  @Test
  fun `tiles west and south of the origin floor rather than truncate`() {
    // Truncation towards zero would map both -1 m and +1 m into tile 0, so two different places would share a
    // tile and one of them would be drawn wrong. The ocean margin puts real ground near the origin, so this is
    // reachable rather than theoretical.
    val span = TileId.TILE_PIXELS.toDouble()

    assertEquals(-1L, TileId.of(0, -1.0, -1.0).tx)
    assertEquals(-1L, TileId.of(0, -span, 0.0).tx)
    assertEquals(-2L, TileId.of(0, -span - 1.0, 0.0).tx)
    assertEquals(0L, TileId.of(0, 0.0, 0.0).tx)
  }

  @Test
  fun `parent and children are inverses`() {
    val tile = TileId(4, 9, -3)
    val parent = tile.parent()!!

    assertEquals(5, parent.level)
    assertTrue(tile in parent.children(), "$parent does not list $tile among its children")
    assertEquals(4, parent.children().size)

    // A parent's bounds must cover all four children exactly, with nothing left over.
    val union = parent.children().map { it.bounds }.reduce { a, b -> a.union(b) }
    assertEquals(parent.bounds, union)
  }

  @Test
  fun `the coarsest level has no parent and the finest no children`() {
    assertNull(TileId(TileId.MAX_LEVEL, 0, 0).parent())
    assertTrue(TileId(0, 0, 0).children().isEmpty())
  }

  @Test
  fun `fitLevel is the coarsest level that still needs only one tile`() {
    for (worldMetres in doubleArrayOf(128_000.0, 512_000.0, 4_096_000.0, 300.0)) {
      val fit = TileId.fitLevel(worldMetres)

      assertEquals(1L, TileId.tilesAcross(worldMetres, fit), "L$fit should hold ${worldMetres}m in one tile")
      if (fit > 0) {
        assertTrue(
          TileId.tilesAcross(worldMetres, fit - 1) > 1L,
          "L${fit - 1} should need more than one tile for ${worldMetres}m, so L$fit is not the coarsest"
        )
      }
    }
  }

  @Test
  fun `fitLevel matches the ladder the design assumes`() {
    // 128 km is Genesis. Recorded so a change to TILE_PIXELS shows up as a failing expectation rather than as
    // silently renumbered levels in a client's cache.
    assertEquals(9, TileId.fitLevel(128_000.0))
    assertEquals(11, TileId.fitLevel(512_000.0))
    assertEquals(14, TileId.fitLevel(4_096_000.0))
  }

  @Test
  fun `covering returns every tile meeting an area and no others`() {
    val area = Aabb(0.0, 0.0, 128_000.0, 128_000.0)
    val tiles = TileId.covering(5, area)

    val across = TileId.tilesAcross(128_000.0, 5)
    assertEquals((across * across).toInt(), tiles.size)
    assertEquals(tiles.size, tiles.distinct().size, "covering returned duplicates")
    assertTrue(tiles.all { it.bounds.intersects(area) }, "covering returned a tile outside the area")
  }

  @Test
  fun `a negative level is refused`() {
    assertFailsWith<IllegalArgumentException> { TileId(-1, 0, 0) }
  }
}
