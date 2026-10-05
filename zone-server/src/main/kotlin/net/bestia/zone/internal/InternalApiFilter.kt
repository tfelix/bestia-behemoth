package net.bestia.zone.internal

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Lets only the login server reach the internal API. It shares the port of the map tiles, which players reach,
 * so a valid service token is the only thing that tells the two callers apart.
 */
@Component
class InternalApiFilter(
  private val serviceTokens: ServiceTokenValidator
) : OncePerRequestFilter() {

  /** The servlet path, which the container has already decoded and normalised, unlike the raw request URI. */
  override fun shouldNotFilter(request: HttpServletRequest): Boolean {
    return !request.servletPath.startsWith(PREFIX)
  }

  override fun doFilterInternal(
    request: HttpServletRequest,
    response: HttpServletResponse,
    filterChain: FilterChain
  ) {
    val header = request.getHeader("Authorization")
    val call = header
      ?.takeIf { it.startsWith(BEARER) }
      ?.let { serviceTokens.validate(it.removePrefix(BEARER).trim()) }

    if (call == null) {
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED)
      return
    }

    request.setAttribute(SERVICE_CALL, call)
    filterChain.doFilter(request, response)
  }

  companion object {
    const val PREFIX = "/internal/"

    /** Request attribute holding the [ServiceTokenValidator.ServiceCall] the token allows. */
    const val SERVICE_CALL = "net.bestia.internal.serviceCall"

    private const val BEARER = "Bearer "
  }
}
