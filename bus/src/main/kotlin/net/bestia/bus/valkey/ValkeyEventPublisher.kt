package net.bestia.bus.valkey

import io.github.oshai.kotlinlogging.KotlinLogging
import io.lettuce.core.RedisClient
import io.lettuce.core.XAddArgs
import net.bestia.bus.BusJson
import net.bestia.bus.BusKeys
import net.bestia.bus.EventPublisher
import net.bestia.bus.contract.ZoneEvent
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Appends zone events to the [BusKeys.EVENTS] stream from a thread of its own, so a caller on the tick never
 * waits for Valkey. When Valkey is slower than the zone the bounded outbox fills, and further events are dropped
 * and counted: a missed notification is a lesser fault than a stalled world.
 */
class ValkeyEventPublisher(
  client: RedisClient,
  capacity: Int = 4096,
  private val retainedEvents: Long = 10_000,
) : EventPublisher, AutoCloseable {

  private val connection = client.connect()
  private val outbox = ArrayBlockingQueue<ZoneEvent>(capacity)
  private val dropped = AtomicLong()

  @Volatile
  private var running = true

  private val drain = Thread.ofPlatform().name("bus-event-outbox").daemon().start { drainLoop() }

  /** Events dropped since start because the outbox was full. */
  val droppedCount: Long
    get() {
      return dropped.get()
    }

  override fun publish(event: ZoneEvent): Boolean {
    if (outbox.offer(event)) {
      return true
    }

    if (dropped.incrementAndGet() % DROP_LOG_EVERY == 1L) {
      LOG.warn { "Event outbox full; ${dropped.get()} events dropped so far" }
    }
    return false
  }

  private fun drainLoop() {
    val trim = XAddArgs.Builder.maxlen(retainedEvents).approximateTrimming()

    while (running || outbox.isNotEmpty()) {
      val event = outbox.poll(1, TimeUnit.SECONDS) ?: continue
      try {
        val fields = mapOf(
          ACCOUNT_FIELD to event.accountId.toString(),
          EVENT_FIELD to BusJson.mapper.writeValueAsString(event),
        )
        connection.sync().xadd(BusKeys.EVENTS, trim, fields)
      } catch (e: Exception) {
        dropped.incrementAndGet()
        LOG.warn(e) { "Could not append ${event::class.simpleName} for account ${event.accountId}" }
      }
    }
  }

  override fun close() {
    running = false
    drain.join(TimeUnit.SECONDS.toMillis(5))
    connection.close()
  }

  companion object {
    const val ACCOUNT_FIELD = "account"
    const val EVENT_FIELD = "event"

    private const val DROP_LOG_EVERY = 100L
    private val LOG = KotlinLogging.logger { }
  }
}
