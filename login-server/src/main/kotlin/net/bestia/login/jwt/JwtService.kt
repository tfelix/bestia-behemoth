package net.bestia.login.jwt

import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import net.bestia.account.Role
import net.bestia.internal.ServiceTokens
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

@Service
class JwtService(
  private val jwtConfig: JwtConfig
) {

  fun createLoginToken(
    accountId: Long,
    role: Role
  ): String {
    val now = Date()
    val expirationDate = LocalDateTime.now().plusMinutes(jwtConfig.loginTokenMinutes)
    val expiration = Date.from(
      expirationDate.atZone(ZoneId.systemDefault()).toInstant()
    )

    val jwt = Jwts.builder()
      // The zone accepts each token once, by this id.
      .id(UUID.randomUUID().toString())
      .subject(accountId.toString())
      .issuer("login")
      .audience().add("zone").and()
      .claim("role", role.name)
      .issuedAt(now)
      .expiration(expiration)
      .signWith(secretKey)
      .compact()

    return jwt
  }

  /** Allows one call to a zone: [scope] on [accountId], once, for [ServiceTokens.LIFETIME_SECONDS]. */
  fun createServiceToken(accountId: Long, scope: String): String {
    val now = Date()

    return Jwts.builder()
      .id(UUID.randomUUID().toString())
      .issuer(ServiceTokens.ISSUER)
      .audience().add(ServiceTokens.AUDIENCE).and()
      .claim(ServiceTokens.ACCOUNT_CLAIM, accountId)
      .claim(ServiceTokens.SCOPE_CLAIM, scope)
      .issuedAt(now)
      .expiration(Date(now.time + ServiceTokens.LIFETIME_SECONDS * MILLIS_PER_SECOND))
      .signWith(secretKey)
      .compact()
  }

  /**
   * Lets a game client call this server's REST API as [accountId]. It names the account only: what the account
   * may do is read from the database on every call, so a ban or a demotion applies at once.
   */
  fun createApiToken(accountId: Long): String {
    val now = Date()

    return Jwts.builder()
      .id(UUID.randomUUID().toString())
      .subject(accountId.toString())
      .issuer(ISSUER)
      .audience().add(API_AUDIENCE).and()
      .issuedAt(now)
      .expiration(Date(now.time + jwtConfig.apiTokenMinutes * SECONDS_PER_MINUTE * MILLIS_PER_SECOND))
      .signWith(secretKey)
      .compact()
  }

  /** The account an api token names, or null for anything else - a zone token or a service token included. */
  fun validateApiToken(token: String): Long? {
    val claims = try {
      Jwts.parser()
        .verifyWith(secretKey)
        .requireIssuer(ISSUER)
        .build()
        .parseSignedClaims(token)
        .payload
    } catch (_: JwtException) {
      return null
    } catch (_: IllegalArgumentException) {
      return null
    }

    if (claims.audience != setOf(API_AUDIENCE) || claims.expiration == null) {
      return null
    }

    return claims.subject?.toLongOrNull()
  }

  private val secretKey: SecretKey by lazy {
    Keys.hmacShaKeyFor(jwtConfig.secret.toByteArray(StandardCharsets.UTF_8))
  }

  private companion object {
    const val ISSUER = "login"
    const val API_AUDIENCE = "login-api"
    const val SECONDS_PER_MINUTE = 60L
    const val MILLIS_PER_SECOND = 1000L
  }
}
