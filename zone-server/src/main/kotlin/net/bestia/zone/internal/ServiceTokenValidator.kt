package net.bestia.zone.internal

import io.github.oshai.kotlinlogging.KotlinLogging
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import net.bestia.internal.ServiceTokens
import net.bestia.zone.config.ZoneConfig
import net.bestia.zone.account.authentication.SingleUseTokenIds
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import javax.crypto.SecretKey

/** Checks the tokens the login server signs for its calls to this zone. */
@Component
class ServiceTokenValidator(
  config: ZoneConfig
) {

  // The secret of the login tokens, and with them it moves to an asymmetric key pair before production.
  private val secretKey: SecretKey = Keys.hmacShaKeyFor(
    config.jwtAuthSecretKey.toByteArray(StandardCharsets.UTF_8)
  )

  private val usedTokenIds = SingleUseTokenIds()

  /** What one service token allows: one [scope] on one account. */
  data class ServiceCall(
    val accountId: Long,
    val scope: String
  )

  /** Null unless [token] is a fresh, unused service token from the login server. */
  fun validate(token: String): ServiceCall? {
    val claims = try {
      Jwts.parser()
        .verifyWith(secretKey)
        .requireIssuer(ServiceTokens.ISSUER)
        .build()
        .parseSignedClaims(token)
        .payload
    } catch (e: JwtException) {
      LOG.debug { "Refused a service token: ${e.message}" }
      return null
    } catch (e: IllegalArgumentException) {
      LOG.debug { "Refused a service token: ${e.message}" }
      return null
    }

    if (claims.audience != setOf(ServiceTokens.AUDIENCE)) {
      LOG.debug { "Refused a service token for audience ${claims.audience}" }
      return null
    }

    val issuedAt = claims.issuedAt ?: return null
    val expiresAt = claims.expiration ?: return null
    if (expiresAt.time - issuedAt.time > ServiceTokens.LIFETIME_SECONDS * MILLIS_PER_SECOND) {
      LOG.warn { "Refused a service token that lives longer than ${ServiceTokens.LIFETIME_SECONDS} s" }
      return null
    }

    val accountId = (claims[ServiceTokens.ACCOUNT_CLAIM] as? Number)?.toLong() ?: return null
    val scope = claims.get(ServiceTokens.SCOPE_CLAIM, String::class.java) ?: return null
    val tokenId = claims.id ?: return null

    if (!usedTokenIds.acceptOnce(tokenId, expiresAt)) {
      LOG.warn { "Refused a replayed service token for account $accountId" }
      return null
    }

    return ServiceCall(accountId, scope)
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }
    private const val MILLIS_PER_SECOND = 1000L
  }
}
