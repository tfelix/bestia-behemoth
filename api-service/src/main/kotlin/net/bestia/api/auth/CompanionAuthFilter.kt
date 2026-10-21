package net.bestia.api.auth

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Every call under `/v1/companion/` carries a companion token; the account it names is the only one the call can
 * act for. The OpenAPI document and the push key are public, and the socket authenticates in its first frame,
 * because a browser cannot put a header on a WebSocket handshake.
 */
@Component
class CompanionAuthFilter(
  private val verifier: CompanionTokenVerifier,
  private val rateLimiter: AccountRateLimiter,
) : OncePerRequestFilter() {

  override fun shouldNotFilter(request: HttpServletRequest): Boolean {
    val path = request.requestURI
    return !path.startsWith(PREFIX) || path in PUBLIC_PATHS
  }

  override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
    val token = request.getHeader(HttpHeaders.AUTHORIZATION)?.removePrefix(BEARER)?.takeIf { it.isNotBlank() }
    val verified = token?.let { verifier.verify(it) }
    if (verified == null) {
      response.status = HttpStatus.UNAUTHORIZED.value()
      return
    }

    if (!rateLimiter.tryAcquire(verified.accountId)) {
      response.status = HttpStatus.TOO_MANY_REQUESTS.value()
      response.setHeader(HttpHeaders.RETRY_AFTER, "60")
      return
    }

    request.setAttribute(ACCOUNT_ID, verified.accountId)
    chain.doFilter(request, response)
  }

  companion object {
    const val ACCOUNT_ID = "net.bestia.api.accountId"

    private const val PREFIX = "/v1/companion/"
    private const val BEARER = "Bearer "
    private val PUBLIC_PATHS = setOf("/v1/companion/ws", "/v1/companion/openapi.json", "/v1/companion/push/key")
  }
}
