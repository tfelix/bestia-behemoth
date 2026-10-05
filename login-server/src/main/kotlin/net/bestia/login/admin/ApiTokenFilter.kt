package net.bestia.login.admin

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import net.bestia.login.jwt.JwtService
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/** Lets only a caller with an api token reach the admin API, and says which account it is. */
@Component
class ApiTokenFilter(
  private val jwtService: JwtService
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
    val accountId = request.getHeader("Authorization")
      ?.takeIf { it.startsWith(BEARER) }
      ?.let { jwtService.validateApiToken(it.removePrefix(BEARER).trim()) }

    if (accountId == null) {
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED)
      return
    }

    request.setAttribute(ACCOUNT_ID, accountId)
    filterChain.doFilter(request, response)
  }

  companion object {
    const val PREFIX = "/api/v1/admin/"

    /** Request attribute holding the id of the account the api token names. */
    const val ACCOUNT_ID = "net.bestia.login.apiAccountId"

    private const val BEARER = "Bearer "
  }
}
