package net.bestia.zone.message

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.socket.ChannelRegistry
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
  private val registry = mockk<ChannelRegistry>(relaxed = true)
  private val channels = mockk<ObjectProvider<ChannelRegistry>>().also { every { it.ifAvailable } returns registry }
  private val sut = AccountInbox(world, TickOutbox(mockk(relaxed = true)), channels)

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

    val a = sut.execute(1L, HandlerLane.TICK) { order.add("tick 1") }
    val b = sut.execute(1L, HandlerLane.IO) { order.add("io") }
    val c = sut.execute(1L, HandlerLane.TICK) { order.add("tick 2") }
    tickUntilDone(a, b, c)

    assertEquals(listOf("tick 1", "io", "tick 2"), order)
  }

  @Test
  fun `a blocked IO task of one account does not hold up another account`() {
    val release = CountDownLatch(1)
    sut.execute(1L, HandlerLane.IO) { release.await() }

    val other = sut.execute(2L, HandlerLane.IO) { }

    other.get(2, TimeUnit.SECONDS)
    release.countDown()
  }

  @Test
  fun `a full inbox closes the connection`() {
    val release = CountDownLatch(1)
    sut.execute(1L, HandlerLane.IO) { release.await() }

    repeat(AccountInbox.CAPACITY + 1) { sut.execute(1L, HandlerLane.IO) { } }

    verify { registry.terminate(1L, "INBOX_OVERFLOW") }
    release.countDown()
  }

  @Test
  fun `a failing task closes the connection and the next task still runs`() {
    sut.execute(1L, HandlerLane.IO) { error("boom") }
    val next = sut.execute(1L, HandlerLane.IO) { }

    next.get(2, TimeUnit.SECONDS)

    verify { registry.terminate(1L, match { it.startsWith("INTERNAL_SERVER_ERROR:") }) }
    assertTrue(next.isDone)
  }
}
