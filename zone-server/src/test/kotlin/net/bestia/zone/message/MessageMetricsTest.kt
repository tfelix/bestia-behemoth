package net.bestia.zone.message

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.BestiaException
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.util.AccountId
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.ObjectProvider
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What the zone counts about the messages clients send, and how long they wait for their turn. */
class MessageMetricsTest {

  private data class Poke(override val playerId: Long) : CMSG

  private class IoHandler(private val behaviour: () -> Boolean) : IoMessageHandler<Poke> {
    override val wire = decoder(MessageCase.PING) { accountId, _ -> Poke(accountId) }

    override fun handle(msg: Poke): Boolean {
      return behaviour()
    }
  }

  private class RunAtOnce : AccountTaskExecutor {
    override fun onTick(accountId: AccountId, task: World.() -> Unit): CompletableFuture<Unit> {
      throw UnsupportedOperationException()
    }

    override fun onIo(accountId: AccountId, task: () -> Unit): CompletableFuture<Unit> {
      task()
      return CompletableFuture.completedFuture(Unit)
    }
  }

  private val meters = SimpleMeterRegistry()
  private val metrics = MessageMetrics(meters)

  private fun processor(behaviour: () -> Boolean): InMessageProcessor {
    return InMessageProcessor(listOf(IoHandler(behaviour)), RunAtOnce(), mockk(relaxed = true), metrics)
  }

  private fun results(result: String): Double {
    return meters.get("zone.message.results")
      .tags("type", "Poke", "lane", "io", "result", result)
      .counter()
      .count()
  }

  @Test
  fun `a handled message is counted and timed`() {
    processor { true }.submit(Poke(1L))

    assertEquals(1.0, results("handled"))
    assertEquals(0.0, results("refused"))
    assertEquals(1L, meters.get("zone.message.duration").tags("type", "Poke", "lane", "io").timer().count())
  }

  @Test
  fun `a refusal is counted as refused`() {
    processor { throw BestiaException(code = "NO", message = "no") }.submit(Poke(1L))

    assertEquals(1.0, results("refused"))
    assertEquals(1L, meters.get("zone.message.duration").tags("type", "Poke", "lane", "io").timer().count())
  }

  @Test
  fun `a bug is counted as failed and still reaches the connection`() {
    assertThrows<MessageHandlingFailedException> { processor { error("boom") }.submit(Poke(1L)) }

    assertEquals(1.0, results("failed"))
    assertEquals(1L, meters.get("zone.message.duration").tags("type", "Poke", "lane", "io").timer().count())
  }

  @Test
  fun `a handler that says it did not handle the message is counted as unhandled`() {
    processor { false }.submit(Poke(1L))

    assertEquals(1.0, results("unhandled"))
  }

  @Test
  fun `every handler has its series before it has handled anything`() {
    processor { true }

    assertEquals(0.0, results("handled"))
    assertEquals(0.0, results("failed"))
  }

  @Nested
  inner class InboxWaiting {
    private val world = testWorld()
    private val connections = mockk<ObjectProvider<ConnectionTerminator>>().also {
      every { it.ifAvailable } returns mockk<ConnectionTerminator>(relaxed = true)
    }
    private val inbox = AccountInbox(world, TickOutbox(mockk(relaxed = true)), connections, metrics)

    @AfterEach
    fun shutDown() {
      inbox.shutdown()
    }

    @Test
    fun `a tick-lane message that has not started is the oldest waiting, until the tick takes it`() {
      assertEquals(0L, inbox.oldestWaitingNanos)

      val done = inbox.onTick(1L) { }
      Thread.sleep(5)
      assertTrue(inbox.oldestWaitingNanos >= TimeUnit.MILLISECONDS.toNanos(5), "nothing has ticked yet")

      world.tick(0f)
      done.get(5, TimeUnit.SECONDS)

      assertEquals(0L, inbox.oldestWaitingNanos)
      assertEquals(1L, meters.get("zone.message.wait").tag("lane", "tick").timer().count())
    }

    @Test
    fun `the wait of an IO message is recorded when it starts`() {
      inbox.onIo(1L) { }.get(5, TimeUnit.SECONDS)

      assertEquals(1L, meters.get("zone.message.wait").tag("lane", "io").timer().count())
      assertEquals(0L, inbox.oldestWaitingNanos)
    }

    @Test
    fun `work that throws is counted against its lane`() {
      val done = inbox.onIo(1L) { error("boom") }

      assertThrows<Exception> { done.get(5, TimeUnit.SECONDS) }
      assertEquals(1.0, meters.get("zone.inbox.failures").tag("lane", "io").counter().count())
    }
  }
}
