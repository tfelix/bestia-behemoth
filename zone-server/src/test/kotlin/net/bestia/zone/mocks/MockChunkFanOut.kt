package net.bestia.zone.mocks

import net.bestia.zone.message.SMSG
import net.bestia.zone.socket.ChunkFanOut
import net.bestia.zone.socket.OutMessageHandler
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * Stands in for [net.bestia.zone.socket.NettyChunkFanOut] when there is no socket.
 *
 * Routes to the same buffer as every other message so integration tests can assert on chunk traffic through
 * [GameClientMockFactory] like anything else. It does not honour the serialise-once contract, and it does not
 * need to - that contract is about Netty buffers and is asserted directly against the real implementation in
 * `NettyChunkFanOutTest`.
 */
@Component
@Profile("no-socket")
class MockChunkFanOut(
  private val outMessageHandler: OutMessageHandler
) : ChunkFanOut {

  override fun fanOut(accountIds: Collection<Long>, message: SMSG): Int {
    accountIds.forEach { outMessageHandler.sendMessage(it, message) }
    return accountIds.size
  }
}
