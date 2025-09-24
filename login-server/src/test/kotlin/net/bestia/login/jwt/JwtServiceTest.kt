package net.bestia.login.jwt

import net.bestia.account.Authority
import net.bestia.login.InternalLoginException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class JwtServiceTest {

    private lateinit var jwtService: JwtService
    private lateinit var jwtConfig: JwtConfig

    @BeforeEach
    fun setUp() {
        jwtConfig = JwtConfig(
            secret = "test-secret-key-that-is-long-enough-for-hmac-sha256-algorithm-requirements",
            expirationDays = 7
        )
        jwtService = JwtService(jwtConfig)
    }

    @Test
    fun `createRefreshToken with valid parameters returns RefreshToken with correct properties`() {
        val accountId = 123L
        val walletAddress = "0x1234567890abcdef1234567890abcdef12345678"
        val tokenId = 456L

        val refreshToken = jwtService.createRefreshToken(accountId, walletAddress, tokenId)

        assertNotNull(refreshToken.jwt)
        assertNotNull(refreshToken.hash)
        assertTrue(refreshToken.expirationDate.isAfter(LocalDateTime.now()))
        assertTrue(refreshToken.expirationDate.isBefore(LocalDateTime.now().plusDays(8)))
    }

    @Test
    fun `createLoginToken with valid parameters returns valid JWT string`() {
        val accountId = 789L
        val permissions = listOf(Authority.KILL, Authority.MAP_MOVE)

        val loginToken = jwtService.createLoginToken(accountId, permissions)

        assertNotNull(loginToken)
        assertTrue(loginToken.isNotEmpty())
        assertTrue(loginToken.contains(".")) // JWT should contain dots as separators
    }

    @Test
    fun `createLoginToken with empty permissions returns valid JWT string`() {
        val accountId = 789L
        val permissions = emptyList<Authority>()

        val loginToken = jwtService.createLoginToken(accountId, permissions)

        assertNotNull(loginToken)
        assertTrue(loginToken.isNotEmpty())
    }

    @Test
    fun `createLoginToken with AUTH_ALL permission returns valid JWT string`() {
        val accountId = 789L
        val permissions = listOf(Authority.AUTH_ALL)

        val loginToken = jwtService.createLoginToken(accountId, permissions)

        assertNotNull(loginToken)
        assertTrue(loginToken.isNotEmpty())
    }

    @Test
    fun `validateRefreshToken with valid token returns correct RefreshTokenClaims`() {
        val accountId = 123L
        val walletAddress = "0x1234567890abcdef1234567890abcdef12345678"
        val tokenId = 456L

        val refreshToken = jwtService.createRefreshToken(accountId, walletAddress, tokenId)
        val claims = jwtService.validateRefreshToken(refreshToken.jwt)

        assertEquals(accountId, claims.accountId)
        assertEquals(walletAddress, claims.wallet)
        assertEquals(tokenId, claims.tokenId)
    }

    @Test
    fun `validateRefreshToken with tampered token throws InternalLoginException`() {
        val accountId = 123L
        val walletAddress = "0x1234567890abcdef1234567890abcdef12345678"
        val tokenId = 456L

        val refreshToken = jwtService.createRefreshToken(accountId, walletAddress, tokenId)
        val tamperedToken = refreshToken.jwt + "tampered"

        assertThrows(InternalLoginException::class.java) {
            jwtService.validateRefreshToken(tamperedToken)
        }
    }

    @Test
    fun `validateRefreshToken with invalid format throws InternalLoginException`() {
        val invalidToken = "invalid.token.format"

        assertThrows(InternalLoginException::class.java) {
            jwtService.validateRefreshToken(invalidToken)
        }
    }

    @Test
    fun `validateRefreshToken with empty token throws InternalLoginException`() {
        assertThrows(InternalLoginException::class.java) {
            jwtService.validateRefreshToken("")
        }
    }

    @Test
    fun `validateRefreshToken with token created by different service throws JwtRefreshException`() {
        // Create a service with different config to simulate wrong issuer
        val differentConfig = JwtConfig(
            secret = "different-secret-key-that-is-long-enough-for-hmac-sha256-algorithm-requirements",
            expirationDays = 7
        )
        val differentService = JwtService(differentConfig)

        val accountId = 123L
        val walletAddress = "0x1234567890abcdef1234567890abcdef12345678"
        val tokenId = 456L

        val refreshToken = differentService.createRefreshToken(accountId, walletAddress, tokenId)

        // This should throw because the signature won't match
        assertThrows(InternalLoginException::class.java) {
            jwtService.validateRefreshToken(refreshToken.jwt)
        }
    }

    @Test
    fun `createRefreshToken generates different hashes for different tokens`() {
        val accountId1 = 123L
        val accountId2 = 124L
        val walletAddress = "0x1234567890abcdef1234567890abcdef12345678"
        val tokenId = 456L

        val refreshToken1 = jwtService.createRefreshToken(accountId1, walletAddress, tokenId)
        val refreshToken2 = jwtService.createRefreshToken(accountId2, walletAddress, tokenId)

        assertNotEquals(refreshToken1.hash, refreshToken2.hash)
        assertNotEquals(refreshToken1.jwt, refreshToken2.jwt)
    }

    @Test
    fun `createRefreshToken generates different tokens for different wallet addresses`() {
        val accountId = 123L
        val walletAddress1 = "0x1234567890abcdef1234567890abcdef12345678"
        val walletAddress2 = "0x9876543210fedcba9876543210fedcba98765432"
        val tokenId = 456L

        val refreshToken1 = jwtService.createRefreshToken(accountId, walletAddress1, tokenId)
        val refreshToken2 = jwtService.createRefreshToken(accountId, walletAddress2, tokenId)

        assertNotEquals(refreshToken1.jwt, refreshToken2.jwt)
        assertNotEquals(refreshToken1.hash, refreshToken2.hash)
    }

    @Test
    fun `createRefreshToken generates different tokens for different token IDs`() {
        val accountId = 123L
        val walletAddress = "0x1234567890abcdef1234567890abcdef12345678"
        val tokenId1 = 456L
        val tokenId2 = 789L

        val refreshToken1 = jwtService.createRefreshToken(accountId, walletAddress, tokenId1)
        val refreshToken2 = jwtService.createRefreshToken(accountId, walletAddress, tokenId2)

        assertNotEquals(refreshToken1.jwt, refreshToken2.jwt)
        assertNotEquals(refreshToken1.hash, refreshToken2.hash)
    }

    @Test
    fun `createLoginToken generates different tokens for different account IDs`() {
        val accountId1 = 123L
        val accountId2 = 124L
        val permissions = listOf(Authority.KILL)

        val loginToken1 = jwtService.createLoginToken(accountId1, permissions)
        val loginToken2 = jwtService.createLoginToken(accountId2, permissions)

        assertNotEquals(loginToken1, loginToken2)
    }

    @Test
    fun `createLoginToken generates different tokens for different permissions`() {
        val accountId = 123L
        val permissions1 = listOf(Authority.KILL)
        val permissions2 = listOf(Authority.MAP_MOVE)

        val loginToken1 = jwtService.createLoginToken(accountId, permissions1)
        val loginToken2 = jwtService.createLoginToken(accountId, permissions2)

        assertNotEquals(loginToken1, loginToken2)
    }

    @Test
    fun `refresh token expiration date respects configuration`() {
        val accountId = 123L
        val walletAddress = "0x1234567890abcdef1234567890abcdef12345678"
        val tokenId = 456L

        val refreshToken = jwtService.createRefreshToken(accountId, walletAddress, tokenId)

        val expectedExpirationDate = LocalDateTime.now().plusDays(jwtConfig.expirationDays.toLong())

        // Allow some tolerance for execution time (1 minute)
        assertTrue(refreshToken.expirationDate.isAfter(expectedExpirationDate.minusMinutes(1)))
        assertTrue(refreshToken.expirationDate.isBefore(expectedExpirationDate.plusMinutes(1)))
    }
}
