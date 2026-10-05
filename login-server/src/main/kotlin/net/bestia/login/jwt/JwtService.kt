package net.bestia.login.jwt

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

  private val secretKey: SecretKey by lazy {
    Keys.hmacShaKeyFor(jwtConfig.secret.toByteArray(StandardCharsets.UTF_8))
  }

  private companion object {
    const val MILLIS_PER_SECOND = 1000L
  }
}
