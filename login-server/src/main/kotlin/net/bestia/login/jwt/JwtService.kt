package net.bestia.login.jwt

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import net.bestia.account.Authority
import net.bestia.login.InternalLoginException
import net.bestia.login.LoginException
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date
import javax.crypto.SecretKey

@Service
class JwtService(
  private val jwtConfig: JwtConfig
) {

  data class RefreshToken(
    val jwt: String,
    val hash: String,
    val expirationDate: LocalDateTime
  )

  data class RefreshTokenClaims(
    val accountId: Long,
    val wallet: String,
    val tokenId: Long
  )

  fun createRefreshToken(
    accountId: Long,
    walletAddress: String,
    tokenId: Long
  ): RefreshToken {
    val now = Date()
    val expirationDate = getExpirationDate()

    val expiration = Date.from(
      expirationDate.atZone(
        ZoneId.systemDefault()
      ).toInstant()
    )

    val jwt = Jwts.builder()
      .subject(accountId.toString())
      .issuer("login")
      .audience().add("refresh").and()
      .claim("tokenId", tokenId)
      .claim("wallet", walletAddress)
      .issuedAt(now)
      .expiration(expiration)
      .signWith(secretKey)
      .compact()

    return RefreshToken(
      jwt = jwt,
      hash = hashToken(jwt),
      expirationDate = expirationDate
    )
  }

  fun createLoginToken(
    accountId: Long,
    permissions: List<Authority>
  ): String {
    val now = Date()
    val expirationDate = LocalDateTime.now().plusMinutes(2)
    val expiration = Date.from(
      expirationDate.atZone(ZoneId.systemDefault()).toInstant()
    )

    val jwt = Jwts.builder()
      .subject(accountId.toString())
      .issuer("login")
      .audience().add("zone").and()
      .claim("permissions", permissions.map { it.name })
      .issuedAt(now)
      .expiration(expiration)
      .signWith(secretKey)
      .compact()

    return jwt
  }

  fun validateRefreshToken(token: String): RefreshTokenClaims {
    try {
      val claims = Jwts.parser()
        .verifyWith(secretKey)
        .build()
        .parseSignedClaims(token)
        .payload

      if (claims.issuer != "login") {
        throw JwtRefreshException("Wrong issues: ${claims.issuer}")
      }

      if (!claims.audience.contains("refresh")) {
        throw JwtRefreshException("Wrong audience: ${claims.audience}")
      }

      // Handle tokenId conversion from Integer to Long safely
      val tokenId = when (val tokenIdClaim = claims["tokenId"]) {
        is Long -> tokenIdClaim
        is Int -> tokenIdClaim.toLong()
        is Number -> tokenIdClaim.toLong()
        else -> throw JwtRefreshException("Invalid tokenId claim type: ${tokenIdClaim?.javaClass}")
      }

      return RefreshTokenClaims(
        accountId = claims.subject.toLong(),
        wallet = claims.get("wallet", String::class.java),
        tokenId = tokenId
      )
    } catch (e: LoginException) {
      throw e
    } catch (e: Exception) {
      throw InternalLoginException("Unknown validation error: ${e.message}", cause = e)
    }
  }

  private fun hashToken(token: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(token.toByteArray(StandardCharsets.UTF_8))

    return hash.joinToString("") { "%02x".format(it) }
  }

  private val secretKey: SecretKey by lazy {
    Keys.hmacShaKeyFor(jwtConfig.secret.toByteArray(StandardCharsets.UTF_8))
  }

  private fun getExpirationDate(): LocalDateTime {
    return LocalDateTime.now().plusDays(jwtConfig.expirationDays.toLong())
  }
}