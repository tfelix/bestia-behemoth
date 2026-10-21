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
import net.bestia.bus.StreamEntry

/**
 * Reads one stream written by a [ValkeyStreamAppender], three ways: [tail] follows it live, [since] replays what
 * a reader missed, and [consume] works through it as a consumer group, so that an entry is handled once however
 * many instances share the group.
 *
 * Each loop holds a connection of its own, because a blocking read would otherwise stall every other command
 * sharing it.
 */
class ValkeyStreamReader<T : Any>(
  private val client: RedisClient,
  private val stream: String,
  private val type: Class<T>,
) : AutoCloseable {

  private val queries = client.connect()
  private val loops = mutableListOf<Thread>()

  @Volatile
  private var running = true

  /** Calls [listener] for every entry appended from now on. */
  fun tail(name: String, listener: (StreamEntry<T>) -> Unit) {
    start(name) {
      val connection = client.connect()
      // From the newest id rather than `$`: `$` is re-read on every call, so entries appended between two reads
      // that returned nothing would be skipped.
      var offset = XReadArgs.StreamOffset.from(stream, newestId())

      connection.use {
        while (running) {
          val batch = readOrNull { connection.sync().xread(BLOCKING_READ, offset) } ?: continue
          for (message in batch) {
            deliver(message, listener)
            offset = XReadArgs.StreamOffset.from(stream, message.id)
          }
        }
      }
    }
  }

  /** The entries after [lastId] for [accountId], oldest first, at most [limit]. Empty if [lastId] has aged out. */
  fun since(lastId: String, accountId: Long, limit: Long = 500): List<StreamEntry<T>> {
    val range = Range.from(Range.Boundary.excluding(lastId), Range.Boundary.unbounded<String>())

    return queries.sync().xrange(stream, range, Limit.from(limit))
      .filter { it.body[ValkeyStreamAppender.ACCOUNT_FIELD] == accountId.toString() }
      .mapNotNull { decode(it) }
  }

  /**
   * Works through the stream as [consumer] of [group]. An entry is acknowledged once [handler] returns, so one
   * that failed or was in hand when the instance stopped is read again after a restart.
   *
   * A group that does not exist yet starts at the stream's end: it is handed what is appended from now on, not
   * the history before it.
   */
  fun consume(group: String, consumer: String, handler: (StreamEntry<T>) -> Unit) {
    createGroup(group)

    start("$group-$consumer") {
      val connection = client.connect()
      val self = Consumer.from(group, consumer)

      connection.use {
        // First what this consumer was given and never acknowledged, then new entries.
        var offset = XReadArgs.StreamOffset.from(stream, "0")
        while (running) {
          val batch = readOrNull { connection.sync().xreadgroup(self, BLOCKING_READ, offset) } ?: continue
          if (batch.isEmpty() && offset.offset == "0") {
            offset = XReadArgs.StreamOffset.lastConsumed(stream)
          }

          for (message in batch) {
            deliver(message, handler)
            connection.sync().xack(stream, group, message.id)
          }
        }
      }
    }
  }

  /** The id of the newest entry, or the lowest id there is when the stream is empty or missing. */
  fun newestId(): String {
    return queries.sync().xrevrange(stream, Range.unbounded(), Limit.from(1)).firstOrNull()?.id ?: "0-0"
  }

  private fun createGroup(group: String) {
    try {
      queries.sync().xgroupCreate(
        XReadArgs.StreamOffset.latest(stream),
        group,
        XGroupCreateArgs.Builder.mkstream(),
      )
    } catch (_: RedisBusyException) {
      // The group exists already, which is the normal case after the first start.
    }
  }

  private fun deliver(message: StreamMessage<String, String>, listener: (StreamEntry<T>) -> Unit) {
    val entry = decode(message) ?: return
    try {
      listener(entry)
    } catch (e: Exception) {
      LOG.error(e) { "Handling $stream entry ${message.id} failed" }
    }
  }

  private fun decode(message: StreamMessage<String, String>): StreamEntry<T>? {
    val json = message.body[ValkeyStreamAppender.PAYLOAD_FIELD] ?: return null
    return try {
      StreamEntry(message.id, BusJson.mapper.readValue(json, type))
    } catch (e: Exception) {
      LOG.warn(e) { "Skipped unreadable $stream entry ${message.id}" }
      null
    }
  }

  private fun <R> readOrNull(read: () -> R): R? {
    return try {
      read()
    } catch (e: Exception) {
      if (running) {
        LOG.warn { "Reading $stream failed (${e.message}); retrying" }
        Thread.sleep(RETRY_PAUSE_MILLIS)
      }
      null
    }
  }

  private fun start(name: String, loop: () -> Unit) {
    loops += Thread.ofPlatform().name("bus-$stream-$name").daemon().start(loop)
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
