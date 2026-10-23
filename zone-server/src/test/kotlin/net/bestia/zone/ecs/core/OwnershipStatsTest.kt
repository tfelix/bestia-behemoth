package net.bestia.zone.ecs.core

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

/** What the world's owner counts about the posted work it runs and the leases it lends. */
class OwnershipStatsTest {

  private val owner = WorldOwnership()

  @BeforeEach
  fun bind() {
    owner.bindTickThread()
  }

  @AfterEach
  fun unbind() {
    owner.unbindTickThread()
  }

  @Test
  fun `posted work is counted by the lane it ran in`() {
    owner.post { }
    owner.runQueued()
    owner.post { }
    owner.post { }
    owner.runQueuedUntil(java.lang.System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(50))

    assertEquals(1L, owner.stats.tickStartTasks)
    assertEquals(2L, owner.stats.betweenStepsTasks)
  }

  @Test
  fun `waiting for work is not counted as work`() {
    owner.runQueuedUntil(java.lang.System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(30))

    assertEquals(0L, owner.stats.betweenStepsTasks)
    assertEquals(0L, owner.stats.betweenStepsNanos)
  }

  @Test
  fun `a task that throws is counted and the rest still run`() {
    owner.post { error("boom") }
    owner.post { }
    owner.runQueued()

    assertEquals(1L, owner.stats.postedFailures)
    assertEquals(2L, owner.stats.tickStartTasks)
  }

  @Test
  fun `a lease is counted on both sides`() {
    val borrower = Thread { owner.withWorld { Thread.sleep(5) } }
    borrower.start()

    val deadline = java.lang.System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
    while (owner.stats.leasesLent == 0L && java.lang.System.nanoTime() < deadline) {
      owner.runQueuedUntil(java.lang.System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(10))
    }
    borrower.join(5_000)

    assertEquals(1L, owner.stats.leasesLent)
    assertEquals(1L, owner.stats.leasesBorrowed)
    assertTrue(owner.stats.leaseLentNanos >= TimeUnit.MILLISECONDS.toNanos(5), "the tick stood still while the block ran")
  }
}
