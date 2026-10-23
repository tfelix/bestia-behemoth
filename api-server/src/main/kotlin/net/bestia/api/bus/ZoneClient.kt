package net.bestia.api.bus

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.api.metrics.AskResult
import net.bestia.api.metrics.ZoneAskMetrics
import net.bestia.bus.NodeBus
import net.bestia.bus.ZoneUnavailableException
import net.bestia.bus.contract.BusReply
import net.bestia.bus.contract.ZoneQuery
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import java.time.Duration
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeoutException

/**
 * Asks the zone and waits, on the request's virtual thread. A reply of any status comes back to the caller; only
 * the cases with no reply at all - no zone listening, or no answer in time - become HTTP errors here.
 */
@Component
class ZoneClient(
  private val bus: NodeBus,
  private val properties: BusClientProperties,
  private val metrics: ZoneAskMetrics,
) {

  fun ask(accountId: Long, query: ZoneQuery, idempotencyKey: String? = null): BusReply {
    val timeout = Duration.ofMillis(properties.requestTimeoutMillis)
    val started = System.nanoTime()

    return try {
      bus.ask(accountId, query, idempotencyKey, timeout).get().also {
        metrics.record(query, AskResult.of(it.status), started)
      }
    } catch (e: ExecutionException) {
      when (val cause = e.cause) {
        is ZoneUnavailableException -> {
          metrics.record(query, AskResult.UNAVAILABLE, started)
          throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "The zone is not up")
        }

        is TimeoutException -> {
          metrics.record(query, AskResult.TIMEOUT, started)
          throw ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "The zone did not answer")
        }

        else -> {
          metrics.record(query, AskResult.ERROR, started)
          LOG.error(cause) { "Asking the zone ${query::class.simpleName} failed" }
          throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "The bus is not reachable")
        }
      }
    }
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
