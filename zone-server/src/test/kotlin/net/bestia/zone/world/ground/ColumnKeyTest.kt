package net.bestia.zone.world.ground

import kotlin.test.Test
import kotlin.test.assertEquals

class ColumnKeyTest {

  @Test
  fun `a column survives being packed and unpacked`() {
    listOf(0 to 0, 1 to 2, 7 to 7, -1 to -1, 3 to -4, Int.MAX_VALUE to Int.MIN_VALUE).forEach { (x, y) ->
      val key = ColumnKey.of(x, y)
      assertEquals(x, ColumnKey.chunkXOf(key), "x lost for ($x, $y)")
      assertEquals(y, ColumnKey.chunkYOf(key), "y lost for ($x, $y)")
    }
  }

  @Test
  fun `two columns never share a key`() {
    val keys = (-4..4).flatMap { x -> (-4..4).map { y -> ColumnKey.of(x, y) } }
    assertEquals(keys.size, keys.toSet().size)
  }
}
