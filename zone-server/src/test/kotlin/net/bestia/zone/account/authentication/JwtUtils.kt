package net.bestia.zone.account.authentication

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Encoders
import io.jsonwebtoken.security.Keys
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*

class JwtUtils {

  // @Test
  fun generateSecretKey() {
    val key = Jwts.SIG.HS256.key().build()
    val base64Key = Encoders.BASE64.encode(key.encoded)

    println("Secret Key: $base64Key")
  }

  // @Test
  fun generateToken() {
    val secretKey = "<SECRET_KEY>"
    val key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secretKey))
    val dateTime = LocalDateTime.now().plusYears(10)
    val futureDate = Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant())

    val token = Jwts.builder()
      .subject("1")
      .claim("selectedMasterEntityId", 1L)
      .issuedAt(Date())
      .expiration(futureDate)
      .signWith(key)
      .compact()

    println("JWT Token: $token")
  }
}