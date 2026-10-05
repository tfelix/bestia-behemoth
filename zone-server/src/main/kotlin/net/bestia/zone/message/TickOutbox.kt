package net.bestia.zone.message

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.core.RateLimitedLog
import net.bestia.zone.socket.OutMessageHandler
import net.bestia.zone.util.AccountId
import org.springframework.stereotype.Component

/**
 * Collects what the tick sends and writes it per account at the end, so a client gets one flush per
 * tick instead of one per changed entity. Sends from any other thread are not taken and go out at once.
 */
@Component
class TickOutbox(
  private val outMessageHandler: OutMessageHandler,
) {

  @Volatile
  private var collectingThread: Thread? = null

  private val pending = LinkedHashMap<AccountId, MutableList<SMSG>>()

  private val failureLog = RateLimitedLog()

  /** Runs [block] and then sends everything it offered, one batch per account in the order offered. */
  fun <T> collect(block: () -> T): T {
    if (collectingThread === Thread.currentThread()) {
      return block()
    }

    collectingThread = Thread.currentThread()
    try {
      return block()
    } finally {
      collectingThread = null
      flush()
    }
  }

  /** Takes [msgs] into the current batch; false when nothing is collecting on this thread. */
  fun offer(accountId: AccountId, msgs: Collection<SMSG>): Boolean {
    if (collectingThread !== Thread.currentThread()) {
      return false
    }

    pending.getOrPut(accountId) { ArrayList() }.addAll(msgs)

    return true
  }

  private fun flush() {
    for ((accountId, msgs) in pending) {
      try {
        outMessageHandler.sendMessages(accountId, msgs)
      } catch (e: Exception) {
        failureLog.emit { held -> LOG.error(e) { "Could not send a batch to account $accountId (+$held more)" } }
      }
    }
    pending.clear()
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
