package net.bestia.api.bus

import io.github.oshai.kotlinlogging.KotlinLogging
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
) {

  fun ask(accountId: Long, query: ZoneQuery, idempotencyKey: String? = null): BusReply {
    val timeout = Duration.ofMillis(properties.requestTimeoutMillis)

    return try {
      bus.ask(accountId, query, idempotencyKey, timeout).get()
    } catch (e: ExecutionException) {
      when (val cause = e.cause) {
        is ZoneUnavailableException -> throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "The zone is not up")
        is TimeoutException -> throw ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "The zone did not answer")
        else -> {
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
