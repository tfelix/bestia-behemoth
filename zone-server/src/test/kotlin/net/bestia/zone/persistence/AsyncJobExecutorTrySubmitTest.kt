package net.bestia.zone.persistence

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AsyncJobExecutorTrySubmitTest {

  @Test
  fun `a job that fits is taken, and one that does not is refused and counted`() {
    val sut = AsyncJobExecutor(workerCount = 1, queueCapacity = 1)
    val release = CountDownLatch(1)
    val started = CountDownLatch(1)

    try {
      assertTrue(sut.trySubmit("k") { started.countDown(); release.await(5, TimeUnit.SECONDS) })
      assertTrue(started.await(5, TimeUnit.SECONDS), "the worker is busy with the first job")
      assertTrue(sut.trySubmit("k") { }, "one more fits in the queue")

      assertFalse(sut.trySubmit("k") { }, "the queue is full")
      assertEquals(1L, sut.rejectedJobs)
    } finally {
      release.countDown()
      sut.shutdown()
    }
  }
}
