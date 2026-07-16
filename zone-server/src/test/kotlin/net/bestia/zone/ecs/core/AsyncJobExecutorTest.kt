package net.bestia.zone.ecs.core

import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class AsyncJobExecutorTest {

  @Test
  fun `submit runs the job off the calling thread`() {
    val sut = AsyncJobExecutor(workerCount = 2)
    val callingThread = Thread.currentThread()
    val jobThread = AtomicReference<Thread?>(null)
    val latch = CountDownLatch(1)

    sut.submit { jobThread.set(Thread.currentThread()); latch.countDown() }

    assertTrue(latch.await(2, TimeUnit.SECONDS))
    assertTrue(jobThread.get() !== callingThread)

    sut.shutdown()
  }

  @Test
  fun `jobs sharing a key run strictly in submission order and never overlap`() {
    val sut = AsyncJobExecutor(workerCount = 4)
    val order = CopyOnWriteArrayList<Int>()
    val latch = CountDownLatch(50)

    for (i in 0 until 50) {
      sut.submit(key = "same-key") {
        order.add(i)
        latch.countDown()
      }
    }

    assertTrue(latch.await(5, TimeUnit.SECONDS))
    assertEquals((0 until 50).toList(), order)

    sut.shutdown()
  }

  @Test
  fun `jobs with different keys can run in parallel`() {
    val sut = AsyncJobExecutor(workerCount = 4)
    val concurrent = AtomicInteger(0)
    val maxConcurrent = AtomicInteger(0)
    val release = CountDownLatch(1)
    val started = CountDownLatch(4)

    repeat(4) { i ->
      sut.submit(key = "key-$i") {
        val now = concurrent.incrementAndGet()
        maxConcurrent.updateAndGet { prev -> maxOf(prev, now) }
        started.countDown()
        release.await(2, TimeUnit.SECONDS)
        concurrent.decrementAndGet()
      }
    }

    assertTrue(started.await(2, TimeUnit.SECONDS))
    release.countDown()

    await().atMost(2, TimeUnit.SECONDS).until { concurrent.get() == 0 }
    assertTrue(maxConcurrent.get() > 1)

    sut.shutdown()
  }

  @Test
  fun `a failing job is caught and does not stop later jobs on the same key`() {
    val sut = AsyncJobExecutor(workerCount = 1)
    val ran = CountDownLatch(1)

    sut.submit(key = "k") { throw RuntimeException("boom") }
    sut.submit(key = "k") { ran.countDown() }

    assertTrue(ran.await(2, TimeUnit.SECONDS))

    sut.shutdown()
  }
}
