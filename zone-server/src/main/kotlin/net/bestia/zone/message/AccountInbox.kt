package net.bestia.zone.message

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PreDestroy
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.socket.ChannelRegistry
import net.bestia.zone.util.AccountId
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Runs each account's messages one at a time and in the order they arrived, each on its [HandlerLane]. A
 * Netty thread only decodes and hands over, so neither the tick nor a slow handler holds up the other
 * connections on its event loop.
 *
 * A full inbox, or a handler that fails, ends the connection: dropping a message silently would leave the
 * client and the server disagreeing about a trade or an equipped item.
 */
@Component
class AccountInbox(
  private val world: WorldView,
  private val outbox: TickOutbox,
  private val channels: ObjectProvider<ChannelRegistry>,
) : AccountTaskExecutor {

  private class Item(val lane: HandlerLane, val task: () -> Unit) {
    val done = CompletableFuture<Unit>()
  }

  private class Mailbox {
    val items = ArrayDeque<Item>()
    var running = false
  }

  private val mailboxes = ConcurrentHashMap<AccountId, Mailbox>()

  private val ioThreads = AtomicInteger()
  private val ioLane = Executors.newFixedThreadPool(IO_THREADS) { r ->
    Thread(r, "zone-io-lane-${ioThreads.getAndIncrement()}").apply { isDaemon = true }
  }

  override fun execute(accountId: AccountId, lane: HandlerLane, task: () -> Unit): CompletableFuture<Unit> {
    val item = Item(lane, task)
    var overflow = false
    var start = false

    mailboxes.compute(accountId) { _, existing ->
      val box = existing ?: Mailbox()
      if (box.items.size >= CAPACITY) {
        overflow = true
      } else {
        box.items.addLast(item)
        start = !box.running
        box.running = true
      }
      box
    }

    if (overflow) {
      LOG.warn { "Inbox of account $accountId is full; closing the connection" }
      terminate(accountId, "INBOX_OVERFLOW")
      item.done.completeExceptionally(IllegalStateException("Inbox of account $accountId is full"))
    } else if (start) {
      runNext(accountId)
    }

    return item.done
  }

  private fun runNext(accountId: AccountId) {
    var next: Item? = null

    // Atomic with execute: an account with nothing left gives its mailbox up, and a new message makes a new one.
    mailboxes.computeIfPresent(accountId) { _, box ->
      next = box.items.pollFirst()
      if (next == null) null else box
    }

    val item = next ?: return
    val turn = {
      runItem(accountId, item)
      runNext(accountId)
    }

    when (item.lane) {
      HandlerLane.TICK -> world.post { turn() }
      HandlerLane.IO -> ioLane.execute(turn)
    }
  }

  private fun runItem(accountId: AccountId, item: Item) {
    try {
      item.task()
      // Done once the replies have left too, so whoever waits for the message sees what it sent.
      outbox.afterFlush { item.done.complete(Unit) }
    } catch (e: Throwable) {
      val errorCode = (e as? MessageHandlingFailedException)?.errorCode ?: UUID.randomUUID().toString()
      if (e !is MessageHandlingFailedException) {
        LOG.error(e) { "Work for account $accountId failed [errorCode=$errorCode]" }
      }
      terminate(accountId, "INTERNAL_SERVER_ERROR:$errorCode")
      item.done.completeExceptionally(e)
    }
  }

  private fun terminate(accountId: AccountId, reason: String) {
    val registry = channels.ifAvailable
    if (registry == null) {
      LOG.warn { "Would close account $accountId ($reason), but there is no socket" }
      return
    }

    registry.terminate(accountId, reason)
  }

  @PreDestroy
  fun shutdown() {
    ioLane.shutdown()
    ioLane.awaitTermination(5, TimeUnit.SECONDS)
  }

  companion object {
    private val LOG = KotlinLogging.logger { }

    /** Messages one account may have waiting; a client that gets this far ahead is broken or hostile. */
    const val CAPACITY = 256

    private const val IO_THREADS = 4
  }
}
