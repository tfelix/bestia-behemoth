package net.bestia.bus.valkey

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.LongAdder

/**
 * What a [ValkeyRequestServer] has done since it started. Plain counters, because this module depends on no
 * metrics library; the zone reads them when its metrics are scraped. Updated from the pub/sub thread and the
 * virtual threads that answer, so every counter is an adder.
 */
class RequestServerStats {

  /** Requests taken off the channel, readable or not. */
  val received = LongAdder()

  /** Requests whose frame could not be decoded; they get no answer. */
  val unreadable = LongAdder()

  /** Requests whose caller had given up before a worker got to them; they get no answer. */
  val expired = LongAdder()

  /** Requests answered from an earlier reply because they repeated an idempotency key. */
  val replayed = LongAdder()

  /** Requests whose handler threw; the caller is told BUSY. */
  val failed = LongAdder()

  private val byType = ConcurrentHashMap<String, TypeStats>()

  /** How often and how long one kind of question was handled. */
  class TypeStats {
    private val handled = LongAdder()
    private val nanos = LongAdder()

    val count: Long
      get() {
        return handled.sum()
      }

    val totalNanos: Long
      get() {
        return nanos.sum()
      }

    internal fun record(elapsedNanos: Long) {
      handled.increment()
      nanos.add(elapsedNanos)
    }
  }

  /** The stats of the question named [type], made on first use. */
  fun of(type: String): TypeStats {
    return byType.computeIfAbsent(type) { TypeStats() }
  }
}
