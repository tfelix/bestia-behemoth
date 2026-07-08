package net.bestia.zone.account.authentication

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import net.bestia.account.Authority
import net.bestia.account.Role
import net.bestia.zone.ZoneConfig
import net.bestia.zone.account.authentication.JwtLoginException
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import javax.crypto.SecretKey

@Component
class LoginTokenValidator(
  config: ZoneConfig
) {

  private val secretKey: SecretKey = Keys.hmacShaKeyFor(
    config.jwtAuthSecretKey.toByteArray(StandardCharsets.UTF_8)
  )

  data class LoginTokenClaims(
    val accountId: Long,
    val role: Role,
    val authorities: Set<Authority>
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

      val roleName = claims.get("role", String::class.java)
        ?: throw JwtLoginException("Missing role claim")

      val role = try {
        Role.valueOf(roleName)
      } catch (e: IllegalArgumentException) {
        throw JwtLoginException("Unknown role: $roleName")
      }

      return LoginTokenClaims(
        accountId = claims.subject.toLong(),
        role = role,
        authorities = role.authorities
      )
    } catch (e: JwtLoginException) {
      throw e
    } catch (e: Exception) {
      throw JwtLoginException("Token validation failed: ${e.message}")
    }
  }
}