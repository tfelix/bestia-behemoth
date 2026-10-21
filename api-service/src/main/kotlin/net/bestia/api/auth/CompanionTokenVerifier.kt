package net.bestia.api.auth

import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import net.bestia.internal.CompanionTokens
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.time.Instant

/** Checks a companion access token: the companion key, the login issuer, exactly the companion audience. */
@Component
class CompanionTokenVerifier(properties: CompanionProperties) {

  private val key = Keys.hmacShaKeyFor(properties.jwtSecret.toByteArray(StandardCharsets.UTF_8))

  data class Verified(val accountId: Long, val expiresAt: Instant)

  /** The token's account and expiry, or null for anything else - a zone token included. */
  fun verify(token: String): Verified? {
    val claims = try {
      Jwts.parser().verifyWith(key).requireIssuer(CompanionTokens.ISSUER).build().parseSignedClaims(token).payload
    } catch (_: JwtException) {
      return null
    } catch (_: IllegalArgumentException) {
      return null
    }

    if (claims.audience != setOf(CompanionTokens.AUDIENCE)) {
      return null
    }
    val accountId = claims.subject?.toLongOrNull() ?: return null
    val expiresAt = claims.expiration?.toInstant() ?: return null

    return Verified(accountId, expiresAt)
  }
}
