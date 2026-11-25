package net.bestia.zone.world.persistence

import net.bestia.zone.world.mana.ManaField
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test

class StoredManaTileTest {

  @Test
  fun `a stored tile reads back the same cells`() {
    val cells = DoubleArray(ManaField.TILE_CELLS * ManaField.TILE_CELLS) { it * 0.001 }

    val row = StoredManaTile(7L, StoredManaTile.bytesOf(cells))

    assertArrayEquals(cells, row.cells())
  }
}
