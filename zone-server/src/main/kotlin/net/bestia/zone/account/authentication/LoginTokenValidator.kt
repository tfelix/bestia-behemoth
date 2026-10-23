package net.bestia.zone.account.authentication

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import net.bestia.account.Authority
import net.bestia.account.Role
import net.bestia.zone.config.ZoneConfig
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.util.Date
import javax.crypto.SecretKey

@Component
class LoginTokenValidator(
  config: ZoneConfig
) {

  // HMAC with the secret login-server signs with, for now. It moves to an asymmetric key pair before production,
  // so that a zone can verify tokens but never mint one.
  private val secretKey: SecretKey = Keys.hmacShaKeyFor(
    config.jwtAuthSecretKey.toByteArray(StandardCharsets.UTF_8)
  )

  /** A token is good for exactly one handshake, so a sniffed token cannot take over the session. */
  private val usedTokenIds = SingleUseTokenIds()

  data class LoginTokenClaims(
    val accountId: Long,
    val role: Role,
    val authorities: Set<Authority>,
    val issuedAt: Date
  )

  fun validateLoginToken(token: String): LoginTokenClaims {
    try {
      val claims = Jwts.parser()
        .verifyWith(secretKey)
        .build()
        .parseSignedClaims(token)
        .payload

      if (claims.issuer != "login") {
        throw JwtLoginException("Wrong issuer: ${claims.issuer}", reason = AuthMetrics.Refusal.WRONG_ISSUER)
      }

      // Exactly, so a token minted for several audiences cannot stand in for a zone login.
      if (claims.audience != setOf("zone")) {
        throw JwtLoginException("Wrong audience: ${claims.audience}", reason = AuthMetrics.Refusal.WRONG_AUDIENCE)
      }

      val roleName = claims.get("role", String::class.java)
        ?: throw JwtLoginException("Missing role claim", reason = AuthMetrics.Refusal.BAD_CLAIMS)

      val role = try {
        Role.valueOf(roleName)
      } catch (e: IllegalArgumentException) {
        throw JwtLoginException("Unknown role: $roleName", reason = AuthMetrics.Refusal.BAD_CLAIMS)
      }

      val tokenId = claims.id ?: throw JwtLoginException("Missing token id", reason = AuthMetrics.Refusal.BAD_CLAIMS)
      val expiresAt = claims.expiration ?: throw JwtLoginException("Missing expiry", reason = AuthMetrics.Refusal.BAD_CLAIMS)
      val issuedAt = claims.issuedAt ?: throw JwtLoginException("Missing issue time", reason = AuthMetrics.Refusal.BAD_CLAIMS)
      if (!usedTokenIds.acceptOnce(tokenId, expiresAt)) {
        throw JwtLoginException("Token already used", reason = AuthMetrics.Refusal.TOKEN_REUSED)
      }

      return LoginTokenClaims(
        accountId = claims.subject.toLong(),
        role = role,
        authorities = role.authorities,
        issuedAt = issuedAt
      )
    } catch (e: JwtLoginException) {
      throw e
    } catch (e: Exception) {
      throw JwtLoginException("Token validation failed: ${e.message}")
    }
  }
}
