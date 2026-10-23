package net.bestia.zone.world.residency

import net.bestia.worldgen.core.ChunkPos
import net.bestia.zone.world.stream.ChunkSubscriptionService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChunkResidencyTest {

  private val residency = ChunkResidency()
  private val firstHeld = mutableListOf<ChunkPos>()
  private val lastReleased = mutableListOf<ChunkPos>()

  private val a = ChunkPos(4, 5, 0)

  init {
    residency.onFirstHolder { firstHeld.add(it) }
    residency.onLastHolder { lastReleased.add(it) }
  }

  @Test
  fun `overlapping holders fire each hook once`() {
    residency.hold(a)
    residency.hold(a)
    residency.release(a)

    assertEquals(listOf(a), firstHeld)
    assertTrue(lastReleased.isEmpty(), "one holder is left")

    residency.release(a)

    assertEquals(listOf(a), lastReleased)
    assertFalse(residency.isResident(a))
  }

  @Test
  fun `a column stays resident while any of its slabs is held`() {
    val above = ChunkPos(4, 5, 1)
    residency.hold(a)
    residency.hold(above)

    residency.release(a)

    assertTrue(residency.isColumnResident(4, 5))
    assertEquals(1, residency.residentColumns)

    residency.release(above)

    assertFalse(residency.isColumnResident(4, 5))
  }

  @Test
  fun `releasing an unheld chunk is refused`() {
    assertThrows<IllegalStateException> { residency.release(a) }
  }

  /** Clients count as one holder: a second account into a chunk is not a second hold. */
  @Test
  fun `clients and another holder share one refcount`() {
    val subscriptions = ChunkSubscriptionService(residency)

    subscriptions.markSent(1L, a)
    subscriptions.markSent(2L, a)
    residency.hold(a)

    subscriptions.forget(1L)
    subscriptions.forget(2L)

    assertTrue(residency.isResident(a), "the other holder still keeps it")
    assertTrue(lastReleased.isEmpty())

    residency.release(a)

    assertEquals(listOf(a), lastReleased)
  }
}
