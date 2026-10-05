package net.bestia.zone.world.stream

import io.mockk.every
import io.mockk.mockk
import net.bestia.worldgen.core.ChunkPos
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * The request budget is what keeps a client from making the tick thread do unbounded work, so nothing a client
 * sends may be free, and reconnecting may not hand it a fresh budget.
 */
class ChunkStreamRequestBudgetTest {

  private val subscriptions = ChunkSubscriptionService()
  private val inbox = ChunkStreamInbox()

  private val system = ChunkStreamSystem(
    chunkService = mockk(relaxed = true) { every { normalise(any()) } answers { firstArg() } },
    subscriptions = subscriptions,
    inbox = inbox,
    fanOut = mockk(relaxed = true),
    settings = ChunkStreamConfig(requestBurst = 4, requestRefillPerTick = 1),
    workers = ChunkWorkers(java.util.concurrent.Executor { it.run() }),
    groundHeight = mockk(relaxed = true),
    oreYield = mockk(relaxed = true),
    connections = mockk(relaxed = true),
  )

  @Test
  fun `positions never offered still cost a token`() {
    inbox.offerRequest(ChunkStreamInbox.Request(ACCOUNT, chunks(6)))

    system.serveRequests()

    assertEquals(1, inbox.pendingRequests, "the rest beyond the budget waits for the next tick")
  }

  @Test
  fun `reconnecting does not refill the budget`() {
    subscriptions.applyManifest(ACCOUNT, chunks(4), emptyList(), reset = true)
    inbox.offerRequest(ChunkStreamInbox.Request(ACCOUNT, chunks(4)))
    system.serveRequests()

    system.forget(ACCOUNT)
    subscriptions.applyManifest(ACCOUNT, chunks(4), emptyList(), reset = true)
    inbox.offerRequest(ChunkStreamInbox.Request(ACCOUNT, chunks(4)))
    system.serveRequests()

    assertEquals(1, inbox.pendingRequests, "the second request is mostly deferred, not served on a fresh budget")
  }

  private fun chunks(count: Int): List<ChunkPos> {
    return (0 until count).map { ChunkPos(it, 0) }
  }

  private companion object {
    const val ACCOUNT = 1L
  }
}
