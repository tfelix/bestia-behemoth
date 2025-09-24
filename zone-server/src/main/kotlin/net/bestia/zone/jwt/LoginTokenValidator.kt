package net.bestia.zone.jwt

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import net.bestia.account.Authority
import net.bestia.zone.ZoneConfig
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import javax.crypto.SecretKey

@Service
class LoginTokenValidator(
  config: ZoneConfig
) {

  private val secretKey: SecretKey = Keys.hmacShaKeyFor(
    config.jwtAuthSecretKey.toByteArray(StandardCharsets.UTF_8)
  )

  data class LoginTokenClaims(
    val accountId: Long,
    val permissions: List<Authority>
  )

  fun validateLoginToken(token: String): LoginTokenClaims {
    try {
      val claims = Jwts.parser()
        .verifyWith(secretKey)
        .build()
        .parseSignedClaims(token)
        .payload

      if (claims.issuer != "login") {
        throw JwtLoginException("Wrong issuer: ${claims.issuer}")
      }

      if (!claims.audience.contains("zone")) {
        throw JwtLoginException("Wrong audience: ${claims.audience}")
      }

      val permissionNames = claims.get("permissions", List::class.java) as List<String>

      val permissions = permissionNames.mapNotNull { permissionName ->
        try {
          Authority.valueOf(permissionName)
        } catch (e: IllegalArgumentException) {
          null // Skip unknown authorities for forward compatibility
        }
      }

      return LoginTokenClaims(
        accountId = claims.subject.toLong(),
        permissions = permissions
      )
    } catch (e: JwtLoginException) {
      throw e
    } catch (e: Exception) {
      throw JwtLoginException("Token validation failed: ${e.message}")
    }
  }
}
