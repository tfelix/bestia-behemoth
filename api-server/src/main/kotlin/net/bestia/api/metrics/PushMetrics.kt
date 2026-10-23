package net.bestia.api.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import net.bestia.api.push.WebPushSender
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.TimeUnit

/** Web push: what each send came to, what was dropped before one, and how the push services answer. */
@Component
class PushMetrics(registry: MeterRegistry) {

  /** Why an event that could have been a notification was not sent. */
  enum class Dropped { RATE_LIMIT, UNWANTED, ONLINE }

  /** The status class of a push service's answer, or [ERROR] when it gave none. */
  enum class StatusClass { SUCCESS_2XX, GONE_404_410, OTHER_4XX, SERVER_5XX, OTHER, ERROR }

  private val outcomes = WebPushSender.Outcome.entries.associateWith {
    Counter.builder("api.push.outcomes")
      .description("Notifications handed to a push service, by what came of it")
      .tag("outcome", it.name.lowercase())
      .register(registry)
  }

  private val dropped = Dropped.entries.associateWith {
    Counter.builder("api.push.dropped")
      .description("Events that were not sent as a notification")
      .tag("reason", it.name.lowercase())
      .register(registry)
  }

  private val sends = StatusClass.entries.associateWith {
    Timer.builder("api.push.send")
      .description("Time to hand one message to a push service")
      .tag("status_class", it.name.lowercase())
      .serviceLevelObjectives(*SLOS)
      .register(registry)
  }

  fun outcome(outcome: WebPushSender.Outcome) {
    outcomes.getValue(outcome).increment()
  }

  fun dropped(reason: Dropped) {
    dropped.getValue(reason).increment()
  }

  /** [httpStatus] is null when the push service never answered. */
  fun sent(httpStatus: Int?, startedNanos: Long) {
    val statusClass = when (httpStatus) {
      null -> StatusClass.ERROR
      in 200..299 -> StatusClass.SUCCESS_2XX
      404, 410 -> StatusClass.GONE_404_410
      in 400..499 -> StatusClass.OTHER_4XX
      in 500..599 -> StatusClass.SERVER_5XX
      else -> StatusClass.OTHER
    }
    sends.getValue(statusClass).record(System.nanoTime() - startedNanos, TimeUnit.NANOSECONDS)
  }

  private companion object {
    val SLOS = arrayOf(100L, 250L, 500L, 1_000L, 2_000L, 5_000L, 10_000L).map { Duration.ofMillis(it) }.toTypedArray()
  }
}
