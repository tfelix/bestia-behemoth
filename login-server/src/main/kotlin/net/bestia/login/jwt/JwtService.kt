package net.bestia.login.jwt

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import net.bestia.account.Role
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date
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

  private val secretKey: SecretKey by lazy {
    Keys.hmacShaKeyFor(jwtConfig.secret.toByteArray(StandardCharsets.UTF_8))
  }
}
