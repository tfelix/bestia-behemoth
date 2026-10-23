package net.bestia.zone.message

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * What the zone does with the messages clients send: how long each type takes, how it ends, and how long it
 * waited for its turn. One set of meters per handler, made when the handler is registered, so a message costs
 * a field read and a counter increment and never builds a tag. The default registry serves tests that build
 * the processor by hand.
 *
 * The type tag is the message class, which a handler registers exactly once, so it cannot grow past the
 * messages a client may send.
 */
@Component
class MessageMetrics(private val registry: MeterRegistry = SimpleMeterRegistry()) {

  /** Which thread runs a handler; see [TickMessageHandler] and [IoMessageHandler]. */
  enum class Lane(val tag: String) {
    TICK("tick"),
    IO("io"),
  }

  /** The meters of one message type. */
  class Handler internal constructor(registry: MeterRegistry, type: String, lane: Lane) {
    val handled = result(registry, type, lane, "handled")
    val refused = result(registry, type, lane, "refused")
    val failed = result(registry, type, lane, "failed")
    val unhandled = result(registry, type, lane, "unhandled")

    val duration: Timer = Timer.builder("zone.message.duration")
      .tag("type", type)
      .tag("lane", lane.tag)
      .description("Time a handler took on one message; a tick-lane handler holds up the whole tick for it")
      .serviceLevelObjectives(*DURATION_BUCKETS)
      .register(registry)

    private fun result(registry: MeterRegistry, type: String, lane: Lane, result: String): Counter {
      return Counter.builder("zone.message.results")
        .tag("type", type)
        .tag("lane", lane.tag)
        .tag("result", result)
        .description(
          "How messages ended: handled; refused with a BestiaException, which the client is told; failed, a bug " +
            "that ends the connection; unhandled, the handler returned false"
        )
        .register(registry)
    }
  }

  private val waits = Lane.entries.associateWith { lane ->
    Timer.builder("zone.message.wait")
      .tag("lane", lane.tag)
      .description("Time from a message arriving to its handler starting: behind the account's earlier messages, then the lane")
      .serviceLevelObjectives(*WAIT_BUCKETS)
      .register(registry)
  }

  private val failures = Lane.entries.associateWith { lane ->
    Counter.builder("zone.inbox.failures")
      .tag("lane", lane.tag)
      .description("Work an account's inbox ran that threw, which ends the connection; a message handler's bug is one")
      .register(registry)
  }

  private val unreadable = Counter.builder("zone.message.unreadable")
    .description("Messages dropped because their payload could not be read; a client that sends these is broken or forged")
    .register(registry)

  /** The meters of [type]'s handler, made on first call and the same afterwards. */
  fun forHandler(type: String, lane: Lane): Handler {
    return Handler(registry, type, lane)
  }

  fun waited(lane: Lane, nanos: Long) {
    waits.getValue(lane).record(nanos, TimeUnit.NANOSECONDS)
  }

  fun inboxFailed(lane: Lane) {
    failures.getValue(lane).increment()
  }

  fun unreadable() {
    unreadable.increment()
  }

  private companion object {
    val DURATION_BUCKETS = arrayOf(
      Duration.ofMillis(1), Duration.ofMillis(5), Duration.ofMillis(25), Duration.ofMillis(100),
    )

    val WAIT_BUCKETS = arrayOf(
      Duration.ofMillis(1), Duration.ofMillis(10), Duration.ofMillis(50), Duration.ofMillis(200), Duration.ofSeconds(1),
    )
  }
}
