package net.bestia.zone.cartography.web

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import net.bestia.zone.account.authentication.LoginTokenValidator
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Says which master is asking for a tile, from the same JWT the game socket was opened with.
 *
 * ### Why the login token and not a ticket of its own
 *
 * The client already holds a signed token: it exchanges its credentials at the login server and sends the result
 * during the socket handshake. Reusing it means the map needs no message to hand out a credential, no service to
 * mint one and no expiry to keep in step with the session - and it is the same secret and the same
 * [LoginTokenValidator] the socket trusts, so there is one place where a token is judged rather than two that can
 * disagree.
 *
 * The token names an *account*, never a master, which is the property that matters here. Which master's charts
 * apply is read from the live session ([ConnectionInfoService]), so a client cannot ask for the fog of a
 * character it is not playing - not even one of its own - by editing a request.
 *
 * ### Plain HTTP
 *
 * The token travels in cleartext, which is the posture the game socket already has: it carries the same token
 * with no TLS either. Worth writing down rather than worth blocking on - terminating TLS is a deployment
 * decision for both, and solving it for one of the two would be a false sense of having solved it.
 *
 * A plain filter rather than Spring Security. There is exactly one rule ("a valid token naming a master with a
 * live session"), and `spring-boot-starter-security` was removed from the build for the reason its own comment
 * in `zone-server/build.gradle` gives.
 */
@Component
class MapAuthFilter(
  private val tokens: LoginTokenValidator,
  private val connectionInfoService: ConnectionInfoService,
) : OncePerRequestFilter() {

  /** Only the map is behind this. Nothing else is served over HTTP, but that is not this filter's business. */
  override fun shouldNotFilter(request: HttpServletRequest): Boolean =
    !request.requestURI.startsWith(PREFIX)

  override fun doFilterInternal(
    request: HttpServletRequest,
    response: HttpServletResponse,
    filterChain: FilterChain
  ) {
    val header = request.getHeader("Authorization")
    if (header == null || !header.startsWith(BEARER)) {
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Bearer token required")
      return
    }

    val accountId = try {
      tokens.validateLoginToken(header.removePrefix(BEARER).trim()).accountId
    } catch (e: Exception) {
      LOG.debug { "Rejected a map request: ${e.message}" }
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid token")
      return
    }

    // Not 401: the token is fine, there is simply no character selected to have charted anything. A client that
    // asks before picking a master gets a distinguishable answer rather than being told to log in again.
    val masterId = try {
      connectionInfoService.getMasterId(accountId)
    } catch (e: Exception) {
      LOG.debug { "Account $accountId asked for a tile with no master selected" }
      response.sendError(HttpServletResponse.SC_CONFLICT, "No master selected")
      return
    }

    request.setAttribute(MASTER_ID, masterId)
    filterChain.doFilter(request, response)
  }

  companion object {

    const val PREFIX = "/map/"

    /** Request attribute the controller reads the resolved master from. */
    const val MASTER_ID = "net.bestia.map.masterId"

    private const val BEARER = "Bearer "

    private val LOG = KotlinLogging.logger { }
  }
}
