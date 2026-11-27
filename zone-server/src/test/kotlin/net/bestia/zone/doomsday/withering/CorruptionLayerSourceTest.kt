package net.bestia.zone.doomsday.withering

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.ground.ColumnKey
import net.bestia.zone.ground.ColumnLevels
import net.bestia.zone.ground.GroundLayer
import net.bestia.zone.ground.GroundOverlayService
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.mana.ManaField.Cell
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CorruptionLayerSourceTest {

  private val field = CorruptionField()
  private val overlay = mockk<GroundOverlayService>(relaxed = true)
  private val worldService = mockk<WorldService> { every { config.chunkSize } returns SIZE }
  private val source = CorruptionLayerSource(field, worldService, overlay)

  @Test
  fun `clean land sends nothing`() {
    assertTrue(source.entryAt(ColumnKey.of(1, 1)).isEmpty)
  }

  @Test
  fun `deeply rotted land is covered in creep`() {
    corruptAround(Cell(0, 0), 1.0)

    val levels = levelsOf(ColumnKey.of(1, 1))

    assertTrue((0 until SIZE * SIZE).all { levels.nibbleAt(it) == 15 })
  }

  @Test
  fun `the creep's edge is ragged, not a straight line`() {
    corruptAround(Cell(0, 0), 1.0)
    // Cell 2 is clean, so the creep ends between the centres of cells 1 and 2, at 192 to 320 m: chunks 6 to 9.
    val edgeByRow = (6..9).flatMap { chunkX ->
      val levels = levelsOf(ColumnKey.of(chunkX, 1))
      (0 until SIZE).mapNotNull { localY ->
        (0 until SIZE).firstOrNull { localX -> levels.nibbleAt(levels.indexOf(localX, localY)) == 0 }
          ?.let { chunkX * SIZE + it }
      }
    }

    assertTrue(edgeByRow.toSet().size > 3, "the creep should end at different metres row by row: $edgeByRow")
  }

  @Test
  fun `a changed cell is announced to the columns it reaches, once`() {
    field.change(Cell(1, 1), 0.5)

    source.announceChanges()
    source.announceChanges()

    // Cell 1 spans 128 to 256 m, and blends half a cell further each way: 64 to 320 m, chunks 2 to 10.
    verify(exactly = 1) { overlay.markLayerDirty(ColumnKey.of(2, 2), GroundLayer.CORRUPTED) }
    verify(exactly = 1) { overlay.markLayerDirty(ColumnKey.of(10, 10), GroundLayer.CORRUPTED) }
    verify(exactly = 0) { overlay.markLayerDirty(ColumnKey.of(1, 2), any()) }
    verify(exactly = 81) { overlay.markLayerDirty(any(), GroundLayer.CORRUPTED) }
  }

  @Test
  fun `the creep starts faint and covers fully once the Withering's status catches`() {
    assertEquals(0, CorruptionLayerSource.levelOf(CorruptionLayerSource.VISIBLE_FROM) shr 4)
    assertEquals(15, CorruptionLayerSource.levelOf(CorruptionLayerSource.FULL_AT) shr 4)
  }

  private fun corruptAround(centre: Cell, intensity: Double) {
    for (dy in -1..1) {
      for (dx in -1..1) {
        field.change(Cell(centre.x + dx, centre.y + dy), intensity)
      }
    }
  }

  private fun levelsOf(column: Long): ColumnLevels {
    val entry = source.entryAt(column)
    val levels = ColumnLevels(SIZE)
    entry.cells.forEachIndexed { i, packed ->
      levels.add(2 * i, (packed.toInt() and 0x0F) shl 4)
      levels.add(2 * i + 1, (packed.toInt() shr 4 and 0x0F) shl 4)
    }
    return levels
  }

  private companion object {
    const val SIZE = 32
  }
}
