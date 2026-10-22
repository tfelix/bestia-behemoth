package net.bestia.api.auth

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.server.ResponseStatusException

/**
 * Every call under `/v1/companion/` names the one account it acts for, in one of two ways:
 *
 * - `Bearer <companion token>`, the companion app's, good for everything;
 * - `Ticket <ticket>`, the game client's HTTP ticket from its socket handshake, good for the map and the guild
 *   emblems only.
 *
 * The OpenAPI document and the push key are public, and the socket authenticates in its first frame, because a
 * browser cannot put a header on a WebSocket handshake. Paths are judged as the servlet decoded and normalised
 * them, which is also the form the routes are matched against.
 */
@Component
class CompanionAuthFilter(
  private val verifier: CompanionTokenVerifier,
  private val tickets: TicketOwners,
  private val rateLimiter: AccountRateLimiter,
) : OncePerRequestFilter() {

  override fun shouldNotFilter(request: HttpServletRequest): Boolean {
    val path = pathOf(request)
    return !path.startsWith(PREFIX) || path in PUBLIC_PATHS
  }

  override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
    val path = pathOf(request)
    val map = MAP_ROUTE.matches(path)
    val ticketRoute = map || EMBLEM_ROUTE.matches(path)
    val accountId = try {
      accountOf(request.getHeader(HttpHeaders.AUTHORIZATION), ticketRoute)
    } catch (e: ResponseStatusException) {
      response.status = e.statusCode.value()
      return
    }

    if (accountId == null) {
      response.status = HttpStatus.UNAUTHORIZED.value()
      return
    }

    if (!rateLimiter.tryAcquire(accountId, map)) {
      response.status = HttpStatus.TOO_MANY_REQUESTS.value()
      response.setHeader(HttpHeaders.RETRY_AFTER, "60")
      return
    }

    request.setAttribute(ACCOUNT_ID, accountId)
    chain.doFilter(request, response)
  }

  private fun accountOf(authorization: String?, ticketRoute: Boolean): Long? {
    val (scheme, credential) = authorization?.split(' ', limit = 2)?.takeIf { it.size == 2 } ?: return null
    if (credential.isBlank()) {
      return null
    }

    return when {
      scheme.equals(BEARER, ignoreCase = true) -> verifier.verify(credential)?.accountId
      scheme.equals(TICKET, ignoreCase = true) && ticketRoute -> tickets.accountFor(credential)
      else -> null
    }
  }

  private fun pathOf(request: HttpServletRequest): String {
    return request.servletPath + (request.pathInfo ?: "")
  }

  companion object {
    const val ACCOUNT_ID = "net.bestia.api.accountId"

    private const val PREFIX = "/v1/companion/"
    private const val BEARER = "Bearer"
    private const val TICKET = "Ticket"
    private val PUBLIC_PATHS = setOf("/v1/companion/ws", "/v1/companion/openapi.json", "/v1/companion/push/key")

    /** The map routes of `MapController`; with [EMBLEM_ROUTE], the only routes a game client's ticket opens. */
    private val MAP_ROUTE = Regex("""^/v1/companion/masters/\d+/map/(meta|[tps]/\d+/-?\d+/-?\d+(\.png|\.json)?)$""")

    private val EMBLEM_ROUTE = Regex("""^/v1/companion/emblems/[0-9a-f]{64}\.png$""")
  }
}
