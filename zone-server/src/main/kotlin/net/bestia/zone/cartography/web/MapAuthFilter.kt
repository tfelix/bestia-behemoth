package net.bestia.zone.cartography.web

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import net.bestia.zone.account.authentication.HttpTicketService
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Says which master is asking for a tile, from the ticket the zone handed the client at the handshake.
 *
 * ### Why a ticket of its own rather than the login token
 *
 * See [HttpTicketService]: the short of it is that a login token expires while a session does not, and the
 * socket judges its token once whereas this filter judges one on every request - so reusing it meant the map
 * quietly stopped working an hour into play while everything else carried on.
 *
 * The ticket names an *account*, never a master, which is the property that matters here. Which master's
 * charts apply is read from the live session ([ConnectionInfoService]), so a client cannot ask for the fog of
 * a character it is not playing - not even one of its own - by editing a request.
 *
 * ### Plain HTTP
 *
 * The ticket travels in cleartext, which is the posture the game socket already has: it carries its own
 * credential with no TLS either. Worth writing down rather than worth blocking on - terminating TLS is a
 * deployment decision for both, and solving it for one of the two would be a false sense of having solved it.
 *
 * A plain filter rather than Spring Security. There is exactly one rule ("a live session's ticket, naming a
 * master"), and `spring-boot-starter-security` was removed from the build for the reason its own comment in
 * `zone-server/build.gradle` gives.
 */
@Component
class MapAuthFilter(
  private val httpTickets: HttpTicketService,
  private val connectionInfoService: ConnectionInfoService,
) : OncePerRequestFilter() {

  /** Only the map is behind this. Nothing else is served over HTTP, but that is not this filter's business. */
  override fun shouldNotFilter(request: HttpServletRequest): Boolean {
    return !request.requestURI.startsWith(PREFIX)
  }

  override fun doFilterInternal(
    request: HttpServletRequest,
    response: HttpServletResponse,
    filterChain: FilterChain
  ) {
    val header = request.getHeader("Authorization")
    if (header == null || !header.startsWith(BEARER)) {
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "HTTP ticket required")
      return
    }

    val accountId = httpTickets.accountFor(header.removePrefix(BEARER).trim())
    if (accountId == null) {
      LOG.debug { "Rejected a map request: no live session holds that ticket" }
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unknown HTTP ticket")
      return
    }

    // Not 401: the ticket is fine, there is simply no character selected to have charted anything. A client
    // that asks before picking a master gets a distinguishable answer rather than being told to log in again.
    val masterId = try {
      connectionInfoService.getMasterId(accountId)
    } catch (_: Exception) {
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
