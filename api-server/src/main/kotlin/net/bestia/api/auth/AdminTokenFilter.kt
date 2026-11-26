package net.bestia.api.auth

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.security.MessageDigest

/**
 * Lets only the holder of the admin token reach `/v1/admin/`. The proxy does not route these paths, so the token is
 * the second wall, not the only one.
 */
@Component
class AdminTokenFilter(properties: AdminProperties) : OncePerRequestFilter() {

  private val token = properties.token?.takeIf { it.isNotBlank() }?.toByteArray()

  override fun shouldNotFilter(request: HttpServletRequest): Boolean {
    return !request.servletPath.startsWith(PREFIX)
  }

  override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
    val presented = request.getHeader(HttpHeaders.AUTHORIZATION)
      ?.takeIf { it.startsWith(BEARER) }
      ?.removePrefix(BEARER)
      ?.trim()
      ?.toByteArray()

    if (token == null || presented == null || !MessageDigest.isEqual(token, presented)) {
      response.status = HttpStatus.UNAUTHORIZED.value()
      return
    }

    chain.doFilter(request, response)
  }

  private companion object {
    const val PREFIX = "/v1/admin/"
    const val BEARER = "Bearer "
  }
}
