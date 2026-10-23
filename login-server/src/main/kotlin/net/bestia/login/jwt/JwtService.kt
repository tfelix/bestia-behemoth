package net.bestia.login.jwt

import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import net.bestia.account.Role
import net.bestia.internal.CompanionTokens
import net.bestia.login.metrics.LoginMetrics
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

@Service
class JwtService(
  private val jwtConfig: JwtConfig,
  private val metrics: LoginMetrics = LoginMetrics()
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

    metrics.tokenIssued(LoginMetrics.TokenType.ZONE)

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

  /**
   * Lets a game client call this server's REST API as [accountId]. It names the account only: what the account
   * may do is read from the database on every call, so a ban or a demotion applies at once.
   */
  fun createApiToken(accountId: Long): String {
    val now = Date()
    metrics.tokenIssued(LoginMetrics.TokenType.API)

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

  /** Lets the companion app call the api-server as [accountId]. Signed with the companion key, not [secretKey]. */
  fun createCompanionToken(accountId: Long): String {
    val now = Date()
    metrics.tokenIssued(LoginMetrics.TokenType.COMPANION)

    return Jwts.builder()
      .id(UUID.randomUUID().toString())
      .subject(accountId.toString())
      .issuer(CompanionTokens.ISSUER)
      .audience().add(CompanionTokens.AUDIENCE).and()
      .issuedAt(now)
      .expiration(Date(now.time + companionTokenMillis))
      .signWith(companionKey)
      .compact()
  }

  val companionTokenMillis: Long
    get() {
      return jwtConfig.companionTokenMinutes * SECONDS_PER_MINUTE * MILLIS_PER_SECOND
    }

  /** The account an api token names, or null for anything else - a zone token included. */
  fun validateApiToken(token: String): Long? {
    val accountId = parseApiToken(token)

    if (accountId == null) {
      metrics.tokenInvalid()
    }

    return accountId
  }

  private fun parseApiToken(token: String): Long? {
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

  private val companionKey: SecretKey by lazy {
    Keys.hmacShaKeyFor(jwtConfig.companionSecret.toByteArray(StandardCharsets.UTF_8))
  }

  private companion object {
    const val ISSUER = "login"
    const val API_AUDIENCE = "login-api"
    const val SECONDS_PER_MINUTE = 60L
    const val MILLIS_PER_SECOND = 1000L
  }
}
