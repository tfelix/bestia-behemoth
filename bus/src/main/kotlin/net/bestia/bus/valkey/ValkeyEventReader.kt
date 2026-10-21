package net.bestia.bus.valkey

import io.github.oshai.kotlinlogging.KotlinLogging
import io.lettuce.core.Consumer
import io.lettuce.core.Limit
import io.lettuce.core.Range
import io.lettuce.core.RedisBusyException
import io.lettuce.core.RedisClient
import io.lettuce.core.StreamMessage
import io.lettuce.core.XGroupCreateArgs
import io.lettuce.core.XReadArgs
import net.bestia.bus.BusJson
import net.bestia.bus.BusKeys
import net.bestia.bus.StreamedEvent
import net.bestia.bus.contract.ZoneEvent

/**
 * The api-service's side of the event stream, read three ways: [tail] follows it live for the WebSocket fan-out,
 * [since] replays what a reconnecting socket missed, and [consume] works through it as a consumer group, so that
 * a notification is sent once however many api-service instances run.
 *
 * Each loop holds a connection of its own, because a blocking read would otherwise stall every other command
 * sharing it.
 */
class ValkeyEventReader(private val client: RedisClient) : AutoCloseable {

  private val queries = client.connect()
  private val loops = mutableListOf<Thread>()

  @Volatile
  private var running = true

  /** Calls [listener] for every event appended from now on. */
  fun tail(name: String, listener: (StreamedEvent) -> Unit) {
    start(name) {
      val connection = client.connect()
      // From the newest id rather than `$`: `$` is re-read on every call, so events appended between two reads
      // that returned nothing would be skipped.
      var offset = XReadArgs.StreamOffset.from(BusKeys.EVENTS, newestId())

      connection.use {
        while (running) {
          val batch = readOrNull { connection.sync().xread(BLOCKING_READ, offset) } ?: continue
          for (message in batch) {
            deliver(message, listener)
            offset = XReadArgs.StreamOffset.from(BusKeys.EVENTS, message.id)
          }
        }
      }
    }
  }

  /** The events after [lastId] for [accountId], oldest first, at most [limit]. Empty if [lastId] has aged out. */
  fun since(lastId: String, accountId: Long, limit: Long = 500): List<StreamedEvent> {
    val range = Range.from(Range.Boundary.excluding(lastId), Range.Boundary.unbounded<String>())

    return queries.sync().xrange(BusKeys.EVENTS, range, Limit.from(limit))
      .filter { it.body[ValkeyEventPublisher.ACCOUNT_FIELD] == accountId.toString() }
      .mapNotNull { decode(it) }
  }

  /**
   * Works through the stream as [consumer] of [group]. An event is acknowledged once [handler] returns, so one
   * that failed or was in hand when the instance stopped is read again after a restart.
   */
  fun consume(group: String, consumer: String, handler: (StreamedEvent) -> Unit) {
    createGroup(group)

    start("$group-$consumer") {
      val connection = client.connect()
      val self = Consumer.from(group, consumer)

      connection.use {
        // First what this consumer was given and never acknowledged, then new events.
        var offset = XReadArgs.StreamOffset.from(BusKeys.EVENTS, "0")
        while (running) {
          val batch = readOrNull { connection.sync().xreadgroup(self, BLOCKING_READ, offset) } ?: continue
          if (batch.isEmpty() && offset.offset == "0") {
            offset = XReadArgs.StreamOffset.lastConsumed(BusKeys.EVENTS)
          }

          for (message in batch) {
            deliver(message, handler)
            connection.sync().xack(BusKeys.EVENTS, group, message.id)
          }
        }
      }
    }
  }

  /** The id of the newest entry, or the lowest id there is when the stream is empty or missing. */
  fun newestId(): String {
    return queries.sync().xrevrange(BusKeys.EVENTS, Range.unbounded(), Limit.from(1)).firstOrNull()?.id ?: "0-0"
  }

  private fun createGroup(group: String) {
    try {
      queries.sync().xgroupCreate(
        XReadArgs.StreamOffset.latest(BusKeys.EVENTS),
        group,
        XGroupCreateArgs.Builder.mkstream(),
      )
    } catch (_: RedisBusyException) {
      // The group exists already, which is the normal case after the first start.
    }
  }

  private fun deliver(message: StreamMessage<String, String>, listener: (StreamedEvent) -> Unit) {
    val event = decode(message) ?: return
    try {
      listener(event)
    } catch (e: Exception) {
      LOG.error(e) { "Handling event ${message.id} failed" }
    }
  }

  private fun decode(message: StreamMessage<String, String>): StreamedEvent? {
    val json = message.body[ValkeyEventPublisher.EVENT_FIELD] ?: return null
    return try {
      StreamedEvent(message.id, BusJson.mapper.readValue(json, ZoneEvent::class.java))
    } catch (e: Exception) {
      LOG.warn(e) { "Skipped unreadable event ${message.id}" }
      null
    }
  }

  private fun <T> readOrNull(read: () -> T): T? {
    return try {
      read()
    } catch (e: Exception) {
      if (running) {
        LOG.warn { "Reading ${BusKeys.EVENTS} failed (${e.message}); retrying" }
        Thread.sleep(RETRY_PAUSE_MILLIS)
      }
      null
    }
  }

  private fun start(name: String, loop: () -> Unit) {
    loops += Thread.ofPlatform().name("bus-events-$name").daemon().start(loop)
  }

  override fun close() {
    running = false
    loops.forEach { it.join(BLOCK_MILLIS * 2) }
    queries.close()
  }

  private companion object {
    const val BLOCK_MILLIS = 2_000L
    const val RETRY_PAUSE_MILLIS = 1_000L
    val BLOCKING_READ: XReadArgs = XReadArgs.Builder.block(BLOCK_MILLIS).count(100)
    val LOG = KotlinLogging.logger { }
  }
}
