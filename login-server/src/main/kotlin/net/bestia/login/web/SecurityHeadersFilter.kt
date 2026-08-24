package net.bestia.login.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * A plain filter rather than Spring Security, following the same reasoning as zone-server's
 * `MapAuthFilter`: there is one rule, it applies to every response, and pulling in the security
 * starter would install a default filter chain that 401s everything else on the way past.
 */
@Component
class SecurityHeadersFilter : OncePerRequestFilter() {

  override fun doFilterInternal(
    request: HttpServletRequest,
    response: HttpServletResponse,
    filterChain: FilterChain
  ) {
    // The login URL carries the session identifier in its query string. Without this, any
    // subresource the page loads would put that identifier in a Referer header bound for a third
    // party.
    response.setHeader("Referrer-Policy", "no-referrer")

    // The auth pages load nothing from anywhere else, so the policy can be absolute. `frame-
    // ancestors none` is what keeps a hostile page from framing the ceremony and harvesting the
    // click that triggers it.
    response.setHeader(
      "Content-Security-Policy",
      "default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self' data:; " +
        "connect-src 'self'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'"
    )

    response.setHeader("X-Content-Type-Options", "nosniff")
    response.setHeader("X-Frame-Options", "DENY")
    response.setHeader("Cache-Control", "no-store")

    filterChain.doFilter(request, response)
  }
}
