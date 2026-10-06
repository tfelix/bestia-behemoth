package net.bestia.zone.message

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.withLoggingContext
import jakarta.annotation.PreDestroy
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.socket.ChannelRegistry
import net.bestia.zone.util.AccountId
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Runs each account's messages one at a time and in the order they arrived, on the tick thread or on an IO
 * thread. A Netty thread only decodes and hands over, so neither the tick nor a slow handler holds up the
 * other connections on its event loop.
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

  private sealed class Item {
    val done = CompletableFuture<Unit>()

    class OnTick(val task: World.() -> Unit) : Item()

    class OnIo(val task: () -> Unit) : Item()
  }

  private class Mailbox {
    val items = ArrayDeque<Item>()
    var running = false
  }

  private val mailboxes = ConcurrentHashMap<AccountId, Mailbox>()
  private val waiting = AtomicInteger()
  private val overflows = AtomicLong()

  private val ioThreads = AtomicInteger()
  private val ioLane = ThreadPoolExecutor(IO_THREADS, IO_THREADS, 0L, TimeUnit.MILLISECONDS, LinkedBlockingQueue()) { r ->
    Thread(r, "zone-io-lane-${ioThreads.getAndIncrement()}").apply { isDaemon = true }
  }

  /** Messages of all accounts that have arrived and not yet started. */
  val pendingMessages: Int
    get() {
      return waiting.get()
    }

  /** Connections closed since start because their inbox was full. */
  val overflowCount: Long
    get() {
      return overflows.get()
    }

  /** IO-lane messages whose account is next, waiting for a free IO thread. */
  val ioQueued: Int
    get() {
      return ioLane.queue.size
    }

  override fun onTick(accountId: AccountId, task: World.() -> Unit): CompletableFuture<Unit> {
    return enqueue(accountId, Item.OnTick(task))
  }

  override fun onIo(accountId: AccountId, task: () -> Unit): CompletableFuture<Unit> {
    return enqueue(accountId, Item.OnIo(task))
  }

  private fun enqueue(accountId: AccountId, item: Item): CompletableFuture<Unit> {
    var overflow = false
    var start = false

    mailboxes.compute(accountId) { _, existing ->
      val box = existing ?: Mailbox()
      if (box.items.size >= CAPACITY) {
        overflow = true
      } else {
        waiting.incrementAndGet()
        box.items.addLast(item)
        start = !box.running
        box.running = true
      }
      box
    }

    if (overflow) {
      overflows.incrementAndGet()
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

    // Atomic with enqueue: an account with nothing left gives its mailbox up, and a new message makes a new one.
    mailboxes.computeIfPresent(accountId) { _, box ->
      next = box.items.pollFirst()
      if (next == null) null else box.also { waiting.decrementAndGet() }
    }

    when (val item = next ?: return) {
      is Item.OnTick -> world.post {
        runItem(accountId, item) { item.task(this) }
        runNext(accountId)
      }

      is Item.OnIo -> ioLane.execute {
        runItem(accountId, item) { item.task() }
        runNext(accountId)
      }
    }
  }

  private fun runItem(accountId: AccountId, item: Item, task: () -> Unit) {
    try {
      withLoggingContext("account" to accountId.toString()) { task() }
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

    registry.disconnect(accountId, reason)
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
