package net.bestia.zone.world.stream

import net.bestia.worldgen.core.ChunkPos
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/**
 * Requests are served by a fixed budget per tick, so the queue in front of it is one a client can grow on purpose.
 * One client's flood must not cost anybody else their requests: a dropped request is never offered again.
 */
class ChunkStreamInboxTest {

  private val inbox = ChunkStreamInbox()

  @Test
  fun `one account flooding the inbox does not evict another account's request`() {
    inbox.offerRequest(ChunkStreamInbox.Request(VICTIM, listOf(ChunkPos(0, 0))))
    repeat(4100) { inbox.offerRequest(ChunkStreamInbox.Request(FLOODER, listOf(ChunkPos(it, 0)))) }

    assertTrue(inbox.drainRequests().any { it.accountId == VICTIM })
  }

  @Test
  fun `the chunks one account has pending are capped`() {
    repeat(3) { round -> inbox.offerRequest(ChunkStreamInbox.Request(FLOODER, (0 until 1000).map { ChunkPos(it, round) })) }

    assertTrue(inbox.drainRequests().sumOf { it.chunks.size } <= 2048)
  }

  private companion object {
    const val VICTIM = 1L
    const val FLOODER = 2L
  }
}
