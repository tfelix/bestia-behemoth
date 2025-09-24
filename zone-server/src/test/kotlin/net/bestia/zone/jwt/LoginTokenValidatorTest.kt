package net.bestia.zone.jwt

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import net.bestia.account.Authority
import net.bestia.zone.ZoneConfig
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
    every { config.jwtAuthSecretKey } returns "test-secret-key-that-is-at-least-32-characters-long-for-hmac-sha256"

    sut = LoginTokenValidator(config)
  }

  @Test
  fun `validateLoginToken with valid token returns correct claims`() {
    // Given
    val accountId = 12345L
    val permissions = listOf(Authority.KILL, Authority.MAP_MOVE)
    val token = createValidLoginToken(accountId, permissions)

    // When
    val result = sut.validateLoginToken(token)

    // Then
    assertEquals(accountId, result.accountId)
    assertEquals(permissions.size, result.permissions.size)
    assertTrue(result.permissions.containsAll(permissions))
  }

  @Test
  fun `validateLoginToken with empty permissions returns empty permissions list`() {
    // Given
    val accountId = 12345L
    val permissions = emptyList<Authority>()
    val token = createValidLoginToken(accountId, permissions)

    // When
    val result = sut.validateLoginToken(token)

    // Then
    assertEquals(accountId, result.accountId)
    assertTrue(result.permissions.isEmpty())
  }

  @Test
  fun `validateLoginToken with all permissions returns all permissions`() {
    // Given
    val accountId = 12345L
    val permissions = Authority.values().toList()
    val token = createValidLoginToken(accountId, permissions)

    // When
    val result = sut.validateLoginToken(token)

    // Then
    assertEquals(accountId, result.accountId)
    assertEquals(permissions.size, result.permissions.size)
    assertTrue(result.permissions.containsAll(permissions))
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

  @Test
  fun `validateLoginToken with unknown permissions filters them out`() {
    // Given
    val token = createTokenWithUnknownPermissions()

    // When
    val result = sut.validateLoginToken(token)

    // Then
    assertEquals(12345L, result.accountId)
    assertEquals(1, result.permissions.size) // Only KILL should remain
    assertTrue(result.permissions.contains(Authority.KILL))
  }

  private fun createValidLoginToken(accountId: Long, permissions: List<Authority>): String {
    val now = Date()
    val expirationDate = LocalDateTime.now().plusMinutes(2)
    val expiration = Date.from(expirationDate.atZone(ZoneId.systemDefault()).toInstant())

    return Jwts.builder()
      .subject(accountId.toString())
      .issuer("login")
      .audience().add("zone").and()
      .claim("permissions", permissions.map { it.name })
      .issuedAt(now)
      .expiration(expiration)
      .signWith(secretKey)
      .compact()
  }

  private fun createTokenWithIssuer(issuer: String): String {
    val now = Date()
    val expirationDate = LocalDateTime.now().plusMinutes(2)
    val expiration = Date.from(expirationDate.atZone(ZoneId.systemDefault()).toInstant())

    return Jwts.builder()
      .subject("12345")
      .issuer(issuer)
      .audience().add("zone").and()
      .claim("permissions", listOf("KILL"))
      .issuedAt(now)
      .expiration(expiration)
      .signWith(secretKey)
      .compact()
  }

  private fun createTokenWithAudience(audience: String): String {
    val now = Date()
    val expirationDate = LocalDateTime.now().plusMinutes(2)
    val expiration = Date.from(expirationDate.atZone(ZoneId.systemDefault()).toInstant())

    return Jwts.builder()
      .subject("12345")
      .issuer("login")
      .audience().add(audience).and()
      .claim("permissions", listOf("KILL"))
      .issuedAt(now)
      .expiration(expiration)
      .signWith(secretKey)
      .compact()
  }

  private fun createExpiredToken(): String {
    val now = Date()
    val expiration = Date.from(LocalDateTime.now().minusMinutes(5).atZone(ZoneId.systemDefault()).toInstant())

    return Jwts.builder()
      .subject("12345")
      .issuer("login")
      .audience().add("zone").and()
      .claim("permissions", listOf("KILL"))
      .issuedAt(now)
      .expiration(expiration)
      .signWith(secretKey)
      .compact()
  }

  private fun createTokenWithDifferentKey(differentKey: SecretKey): String {
    val now = Date()
    val expirationDate = LocalDateTime.now().plusMinutes(2)
    val expiration = Date.from(expirationDate.atZone(ZoneId.systemDefault()).toInstant())

    return Jwts.builder()
      .subject("12345")
      .issuer("login")
      .audience().add("zone").and()
      .claim("permissions", listOf("KILL"))
      .issuedAt(now)
      .expiration(expiration)
      .signWith(differentKey)
      .compact()
  }

  private fun createTokenWithUnknownPermissions(): String {
    val now = Date()
    val expirationDate = LocalDateTime.now().plusMinutes(2)
    val expiration = Date.from(expirationDate.atZone(ZoneId.systemDefault()).toInstant())

    return Jwts.builder()
      .subject("12345")
      .issuer("login")
      .audience().add("zone").and()
      .claim("permissions", listOf("KILL", "UNKNOWN_PERMISSION", "ANOTHER_UNKNOWN"))
      .issuedAt(now)
      .expiration(expiration)
      .signWith(secretKey)
      .compact()
  }
}
