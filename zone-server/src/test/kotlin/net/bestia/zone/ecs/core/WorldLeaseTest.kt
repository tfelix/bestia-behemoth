package net.bestia.zone.ecs.core

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.concurrent.thread

/** A lease comes back to the tick whatever happens to the thread that asked for it. */
class WorldLeaseTest {

  private val owner = WorldOwnership()

  @BeforeEach
  fun bind() {
    owner.bindTickThread()
  }

  @AfterEach
  fun unbind() {
    owner.unbindTickThread()
  }

  /** Awaitility interrupts the thread that checks a condition when it gives up, and a condition may read the world. */
  @Test
  fun `a borrower interrupted while it waits does not leave the tick waiting for it`() {
    val borrower = thread {
      runCatching { owner.withWorld { } }
    }
    while (owner.pendingPosts == 0) {
      Thread.onSpinWait()
    }

    borrower.interrupt()
    borrower.join(5_000)

    assertTimeoutPreemptively(Duration.ofSeconds(5)) {
      owner.runQueued()
    }
  }
}
