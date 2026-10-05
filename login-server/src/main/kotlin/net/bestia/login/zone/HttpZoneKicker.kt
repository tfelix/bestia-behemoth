package net.bestia.login.zone

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.account.KickReason
import net.bestia.internal.ServiceTokens
import net.bestia.login.jwt.JwtService
import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException

/**
 * Tells every zone the account may play on to end its session.
 *
 * Best effort: a zone that cannot be reached is logged and skipped. The player there keeps playing until they
 * disconnect, but cannot log in again, because their tokens are already gone.
 */
@Component
class HttpZoneKicker(
  private val zones: ZoneDirectory,
  private val jwtService: JwtService,
  private val config: ZoneDirectoryConfig
) {

  private val http = HttpClient.newBuilder()
    .connectTimeout(Duration.ofMillis(config.requestTimeoutMillis))
    .build()

  /** Answers the names of the zones that confirmed the kick. */
  fun kick(accountId: Long, reason: KickReason): List<String> {
    // All calls are sent before any answer is awaited, so the zones are asked in parallel.
    val calls = zones.candidatesFor(accountId).map { zone -> zone to send(zone, accountId, reason) }

    return calls.mapNotNull { (zone, call) -> zone.name.takeIf { confirmed(zone, accountId, call) } }
  }

  private fun send(zone: ZoneEndpoint, accountId: Long, reason: KickReason): CompletableFuture<HttpResponse<Void>> {
    val request = HttpRequest.newBuilder(URI.create(zone.baseUrl.trimEnd('/') + ServiceTokens.kickPath(accountId)))
      .timeout(Duration.ofMillis(config.requestTimeoutMillis))
      .header("Authorization", "Bearer ${jwtService.createServiceToken(accountId, ServiceTokens.KICK_SCOPE)}")
      .header("Content-Type", "application/json")
      .POST(HttpRequest.BodyPublishers.ofString("""{"reason":"${reason.name}"}"""))
      .build()

    return http.sendAsync(request, HttpResponse.BodyHandlers.discarding())
  }

  private fun confirmed(zone: ZoneEndpoint, accountId: Long, call: CompletableFuture<HttpResponse<Void>>): Boolean {
    val status = try {
      call.join().statusCode()
    } catch (e: CompletionException) {
      LOG.error(e.cause) { "Could not reach zone ${zone.name} to kick account $accountId" }
      return false
    }

    if (status != NO_CONTENT) {
      LOG.error { "Zone ${zone.name} answered $status to the kick of account $accountId" }
      return false
    }

    return true
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }
    private const val NO_CONTENT = 204
  }
}
