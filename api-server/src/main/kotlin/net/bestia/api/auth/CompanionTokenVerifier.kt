package net.bestia.api.auth

import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import net.bestia.api.metrics.AuthMetrics
import net.bestia.internal.CompanionTokens
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.time.Instant

/** Checks a companion access token: the companion key, the login issuer, exactly the companion audience. */
@Component
class CompanionTokenVerifier(properties: CompanionProperties, private val metrics: AuthMetrics) {

  private val key = Keys.hmacShaKeyFor(properties.jwtSecret.toByteArray(StandardCharsets.UTF_8))

  data class Verified(val accountId: Long, val expiresAt: Instant)

  /** The token's account and expiry, or null for anything else - a zone token included. */
  fun verify(token: String): Verified? {
    val claims = try {
      Jwts.parser().verifyWith(key).requireIssuer(CompanionTokens.ISSUER).build().parseSignedClaims(token).payload
    } catch (_: ExpiredJwtException) {
      return failed(AuthMetrics.TokenFailure.EXPIRED)
    } catch (_: JwtException) {
      return failed(AuthMetrics.TokenFailure.BAD_JWT)
    } catch (_: IllegalArgumentException) {
      return failed(AuthMetrics.TokenFailure.BAD_JWT)
    }

    if (claims.audience != setOf(CompanionTokens.AUDIENCE)) {
      return failed(AuthMetrics.TokenFailure.WRONG_AUDIENCE)
    }
    val accountId = claims.subject?.toLongOrNull() ?: return failed(AuthMetrics.TokenFailure.MISSING_SUBJECT)
    val expiresAt = claims.expiration?.toInstant() ?: return failed(AuthMetrics.TokenFailure.MISSING_EXPIRY)

    return Verified(accountId, expiresAt)
  }

  private fun failed(reason: AuthMetrics.TokenFailure): Verified? {
    metrics.tokenFailed(reason)
    return null
  }
}
