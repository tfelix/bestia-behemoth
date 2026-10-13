package net.bestia.zone.ground

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The due-time bookkeeping under the overlay's throttle: one entry per column, earliest due wins. */
class OwedColumnsTest {

  private val owed = OwedColumns()

  private fun drain(nowSecond: Long): Map<Long, Set<Long>> {
    val sent = HashMap<Long, Set<Long>>()
    owed.drainDue(nowSecond) { column, accounts -> sent[column] = accounts.toSet() }
    return sent
  }

  @Test
  fun `a column is not handed over before it is due`() {
    owed.owe(columnKey = 1, accounts = listOf(10L), dueSecond = 100)

    assertFalse(owed.isDue(99))
    assertEquals(emptyMap(), drain(99))
    assertEquals(1, owed.size)
  }

  @Test
  fun `a column is handed over once it is due and then forgotten`() {
    owed.owe(columnKey = 1, accounts = listOf(10L), dueSecond = 100)

    assertTrue(owed.isDue(100))
    assertEquals(mapOf(1L to setOf(10L)), drain(100))
    assertEquals(0, owed.size)
    assertEquals(emptyMap(), drain(1_000))
  }

  @Test
  fun `marking a column again adds its accounts and keeps the earlier due time`() {
    owed.owe(columnKey = 1, accounts = listOf(10L), dueSecond = 100)
    owed.owe(columnKey = 1, accounts = listOf(11L), dueSecond = 500)

    assertEquals(1, owed.size)
    assertEquals(mapOf(1L to setOf(10L, 11L)), drain(100))
  }

  @Test
  fun `a later mark can only bring a column forward`() {
    owed.owe(columnKey = 1, accounts = listOf(10L), dueSecond = 500)
    owed.owe(columnKey = 1, accounts = listOf(11L), dueSecond = Long.MIN_VALUE)

    assertTrue(owed.isDue(0))
    assertEquals(mapOf(1L to setOf(10L, 11L)), drain(0))
  }

  @Test
  fun `columns fall due on their own clocks`() {
    owed.owe(columnKey = 1, accounts = listOf(10L), dueSecond = 100)
    owed.owe(columnKey = 2, accounts = listOf(10L), dueSecond = 200)

    assertEquals(setOf(1L), drain(150).keys)
    assertFalse(owed.isDue(150), "the gate was left open by a column that had already gone")
    assertEquals(setOf(2L), drain(200).keys)
  }

  @Test
  fun `a column whose accounts have all left is dropped without being handed over`() {
    owed.owe(columnKey = 1, accounts = listOf(10L), dueSecond = 100)
    owed.retainOnly(columnKey = 1, accounts = emptyList())

    assertEquals(emptyMap(), drain(100))
    assertEquals(0, owed.size)
  }

  @Test
  fun `accounts that left are not handed anything but those that stayed are`() {
    owed.owe(columnKey = 1, accounts = listOf(10L, 11L), dueSecond = 100)
    owed.retainOnly(columnKey = 1, accounts = listOf(11L))

    assertEquals(mapOf(1L to setOf(11L)), drain(100))
  }

  @Test
  fun `a forgotten column is never handed over`() {
    owed.owe(columnKey = 1, accounts = listOf(10L), dueSecond = 100)
    owed.forget(1)

    assertEquals(emptyMap(), drain(100))
  }

  @Test
  fun `draining reports how many columns went out`() {
    owed.owe(columnKey = 1, accounts = listOf(10L), dueSecond = 100)
    owed.owe(columnKey = 2, accounts = listOf(10L), dueSecond = 100)
    owed.owe(columnKey = 3, accounts = listOf(10L), dueSecond = 900)

    assertEquals(2, owed.drainDue(100) { _, _ -> })
  }

  @Test
  fun `an empty queue is never due`() {
    assertFalse(owed.isDue(Long.MAX_VALUE - 1))
    assertEquals(0, owed.drainDue(Long.MAX_VALUE - 1) { _, _ -> })
  }
}
