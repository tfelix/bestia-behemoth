package net.bestia.zone.jwt

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import net.bestia.account.Role
import net.bestia.zone.ZoneConfig
import net.bestia.zone.account.authentication.JwtLoginException
import net.bestia.zone.account.authentication.LoginTokenValidator
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date
import javax.crypto.SecretKey
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExtendWith(MockKExtension::class)
class LoginTokenValidatorTest {

  private val secret = "test-secret-key-that-is-at-least-32-characters-long-for-hmac-sha256"
  private val secretKey = Keys.hmacShaKeyFor(secret.toByteArray(StandardCharsets.UTF_8))

  @MockK
  private lateinit var config: ZoneConfig

  private lateinit var sut: LoginTokenValidator

  @BeforeEach
  fun setUp() {
    every { config.jwtAuthSecretKey } returns secret

    sut = LoginTokenValidator(config)
  }

  @Test
  fun `validateLoginToken with valid token returns role and derived authorities`() {
    // Given
    val accountId = 12345L
    val token = createValidLoginToken(accountId, Role.GM)

    // When
    val result = sut.validateLoginToken(token)

    // Then
    assertEquals(accountId, result.accountId)
    assertEquals(Role.GM, result.role)
    assertEquals(Role.GM.authorities, result.authorities)
  }

  @Test
  fun `validateLoginToken with USER role returns the user authorities`() {
    // Given
    val accountId = 12345L
    val token = createValidLoginToken(accountId, Role.USER)

    // When
    val result = sut.validateLoginToken(token)

    // Then
    assertEquals(Role.USER.authorities, result.authorities)
  }

  @Test
  fun `validateLoginToken with SUPER_GM role returns all authorities`() {
    // Given
    val accountId = 12345L
    val token = createValidLoginToken(accountId, Role.SUPER_GM)

    // When
    val result = sut.validateLoginToken(token)

    // Then
    assertEquals(Role.SUPER_GM.authorities, result.authorities)
  }

  @Test
  fun `validateLoginToken with unknown role throws JwtLoginException`() {
    // Given
    val token = createTokenWithRoleClaim("NOT_A_ROLE")

    // When & Then
    val exception = assertThrows<JwtLoginException> {
      sut.validateLoginToken(token)
    }
    assertTrue(exception.message!!.contains("Unknown role"))
  }

  @Test
  fun `validateLoginToken with missing role claim throws JwtLoginException`() {
    // Given
    val token = createTokenWithoutRoleClaim()

    // When & Then
    val exception = assertThrows<JwtLoginException> {
      sut.validateLoginToken(token)
    }
    assertTrue(exception.message!!.contains("Missing role claim"))
  }

  @Test
  fun `validateLoginToken with wrong issuer throws JwtLoginException`() {
    // Given
    val token = createTokenWithIssuer("wrong-issuer")

    // When & Then
    val exception = assertThrows<JwtLoginException> {
      sut.validateLoginToken(token)
    }
    assertTrue(exception.message!!.contains("Wrong issuer"))
  }

  @Test
  fun `validateLoginToken with wrong audience throws JwtLoginException`() {
    // Given
    val token = createTokenWithAudience("refresh")

    // When & Then
    val exception = assertThrows<JwtLoginException> {
      sut.validateLoginToken(token)
    }
    assertTrue(exception.message!!.contains("Wrong audience"))
  }

  @Test
  fun `validateLoginToken with expired token throws JwtLoginException`() {
    // Given
    val token = createExpiredToken()

    // When & Then
    val exception = assertThrows<JwtLoginException> {
      sut.validateLoginToken(token)
    }
    assertTrue(exception.message!!.contains("Token validation failed"))
  }

  @Test
  fun `validateLoginToken with malformed token throws JwtLoginException`() {
    // Given
    val malformedToken = "invalid.jwt.token"

    // When & Then
    val exception = assertThrows<JwtLoginException> {
      sut.validateLoginToken(malformedToken)
    }
    assertTrue(exception.message!!.contains("Token validation failed"))
  }

  @Test
  fun `validateLoginToken with token signed by different key throws JwtLoginException`() {
    // Given
    val differentSecretKey =
      Keys.hmacShaKeyFor("different-secret-key-that-is-at-least-32-characters-long".toByteArray(StandardCharsets.UTF_8))
    val token = createTokenWithDifferentKey(differentSecretKey)

    // When & Then
    val exception = assertThrows<JwtLoginException> {
      sut.validateLoginToken(token)
    }
    assertTrue(exception.message!!.contains("Token validation failed"))
  }

  private fun futureExpiration(): Date =
    Date.from(LocalDateTime.now().plusMinutes(2).atZone(ZoneId.systemDefault()).toInstant())

  private fun createValidLoginToken(accountId: Long, role: Role): String {
    return Jwts.builder()
      .subject(accountId.toString())
      .issuer("login")
      .audience().add("zone").and()
      .claim("role", role.name)
      .issuedAt(Date())
      .expiration(futureExpiration())
      .signWith(secretKey)
      .compact()
  }

  private fun createTokenWithRoleClaim(role: String): String {
    return Jwts.builder()
      .subject("12345")
      .issuer("login")
      .audience().add("zone").and()
      .claim("role", role)
      .issuedAt(Date())
      .expiration(futureExpiration())
      .signWith(secretKey)
      .compact()
  }

  private fun createTokenWithoutRoleClaim(): String {
    return Jwts.builder()
      .subject("12345")
      .issuer("login")
      .audience().add("zone").and()
      .issuedAt(Date())
      .expiration(futureExpiration())
      .signWith(secretKey)
      .compact()
  }

  private fun createTokenWithIssuer(issuer: String): String {
    return Jwts.builder()
      .subject("12345")
      .issuer(issuer)
      .audience().add("zone").and()
      .claim("role", "GM")
      .issuedAt(Date())
      .expiration(futureExpiration())
      .signWith(secretKey)
      .compact()
  }

  private fun createTokenWithAudience(audience: String): String {
    return Jwts.builder()
      .subject("12345")
      .issuer("login")
      .audience().add(audience).and()
      .claim("role", "GM")
      .issuedAt(Date())
      .expiration(futureExpiration())
      .signWith(secretKey)
      .compact()
  }

  private fun createExpiredToken(): String {
    return Jwts.builder()
      .subject("12345")
      .issuer("login")
      .audience().add("zone").and()
      .claim("role", "GM")
      .issuedAt(Date())
      .expiration(Date.from(LocalDateTime.now().minusMinutes(5).atZone(ZoneId.systemDefault()).toInstant()))
      .signWith(secretKey)
      .compact()
  }

  private fun createTokenWithDifferentKey(differentKey: SecretKey): String {
    return Jwts.builder()
      .subject("12345")
      .issuer("login")
      .audience().add("zone").and()
      .claim("role", "GM")
      .issuedAt(Date())
      .expiration(futureExpiration())
      .signWith(differentKey)
      .compact()
  }
}
