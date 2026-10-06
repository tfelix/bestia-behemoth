package net.bestia.zone.message

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.ecs.core.testWorld
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AccountInboxTest {

  private val world = testWorld()
  private val terminator = mockk<ConnectionTerminator>(relaxed = true)
  private val connections = mockk<ObjectProvider<ConnectionTerminator>>().also { every { it.ifAvailable } returns terminator }
  private val sut = AccountInbox(world, TickOutbox(mockk(relaxed = true)), connections)

  @AfterEach
  fun shutDown() {
    sut.shutdown()
  }

  /** Stands in for the tick thread: tick-lane work only runs when the world ticks. */
  private fun tickUntilDone(vararg futures: java.util.concurrent.CompletableFuture<Unit>) {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
    while (futures.any { !it.isDone } && System.nanoTime() < deadline) {
      world.tick(0f)
      Thread.sleep(1)
    }
  }

  @Test
  fun `one account's work runs in order across both lanes`() {
    val order = CopyOnWriteArrayList<String>()

    val a = sut.onTick(1L) { order.add("tick 1") }
    val b = sut.onIo(1L) { order.add("io") }
    val c = sut.onTick(1L) { order.add("tick 2") }
    tickUntilDone(a, b, c)

    assertEquals(listOf("tick 1", "io", "tick 2"), order)
  }

  @Test
  fun `a blocked IO task of one account does not hold up another account`() {
    val release = CountDownLatch(1)
    sut.onIo(1L) { release.await() }

    val other = sut.onIo(2L) { }

    other.get(2, TimeUnit.SECONDS)
    release.countDown()
  }

  @Test
  fun `a full inbox closes the connection`() {
    val release = CountDownLatch(1)
    sut.onIo(1L) { release.await() }

    repeat(AccountInbox.CAPACITY + 1) { sut.onIo(1L) { } }

    verify { terminator.disconnect(1L, "INBOX_OVERFLOW") }
    assertEquals(1L, sut.overflowCount)
    release.countDown()
  }

  @Test
  fun `waiting messages are counted until they start`() {
    val release = CountDownLatch(1)
    sut.onIo(1L) { release.await() }
    val last = (1..3).map { sut.onIo(1L) { } }.last()

    assertEquals(3, sut.pendingMessages)

    release.countDown()
    last.get(2, TimeUnit.SECONDS)
    assertEquals(0, sut.pendingMessages)
  }

  @Test
  fun `a failing task closes the connection and the next task still runs`() {
    sut.onIo(1L) { error("boom") }
    val next = sut.onIo(1L) { }

    next.get(2, TimeUnit.SECONDS)

    verify { terminator.disconnect(1L, match { it.startsWith("INTERNAL_SERVER_ERROR:") }) }
    assertTrue(next.isDone)
  }
}
