package net.bestia.api.bus

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bus.LoginBus
import net.bestia.bus.LoginUnavailableException
import net.bestia.bus.contract.LoginCommand
import net.bestia.bus.contract.LoginReply
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import java.time.Duration
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeoutException

/**
 * Sends a GM command to the login server and waits, on the request's virtual thread. Like [ZoneClient], only the
 * cases with no reply at all become HTTP errors here.
 */
@Component
class LoginClient(
  private val bus: LoginBus,
  private val properties: BusClientProperties,
) {

  fun send(actorAccountId: Long, command: LoginCommand): LoginReply.Outcome {
    val timeout = Duration.ofMillis(properties.requestTimeoutMillis)

    try {
      return bus.send(actorAccountId, command, timeout).get()
    } catch (e: ExecutionException) {
      when (val cause = e.cause) {
        is LoginUnavailableException -> {
          throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "The login server is not up")
        }

        is TimeoutException -> {
          throw ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "The login server did not answer")
        }

        else -> {
          LOG.error(cause) { "Sending ${command::class.simpleName} to the login server failed" }
          throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "The bus is not reachable")
        }
      }
    }
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
