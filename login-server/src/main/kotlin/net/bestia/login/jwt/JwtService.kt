package net.bestia.login.jwt

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

  private val secretKey: SecretKey by lazy {
    Keys.hmacShaKeyFor(jwtConfig.secret.toByteArray(StandardCharsets.UTF_8))
  }

  private val companionKey: SecretKey by lazy {
    Keys.hmacShaKeyFor(jwtConfig.companionSecret.toByteArray(StandardCharsets.UTF_8))
  }

  private companion object {
    const val ISSUER = "login"
    const val SECONDS_PER_MINUTE = 60L
    const val MILLIS_PER_SECOND = 1000L
  }
}
