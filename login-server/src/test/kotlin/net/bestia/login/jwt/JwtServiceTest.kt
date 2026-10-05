package net.bestia.login.jwt

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import net.bestia.account.Role
import net.bestia.internal.ServiceTokens
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.util.Date

class JwtServiceTest {

    private val secret = "test-secret-key-that-is-long-enough-for-hmac-sha256-algorithm-requirements"

    private lateinit var jwtService: JwtService
    private lateinit var jwtConfig: JwtConfig

    @BeforeEach
    fun setUp() {
        jwtConfig = JwtConfig(
            secret = secret,
            loginTokenMinutes = 60
        )
        jwtService = JwtService(jwtConfig)
    }

    @Test
    fun `createLoginToken with valid parameters returns valid JWT string`() {
        val accountId = 789L

        val loginToken = jwtService.createLoginToken(accountId, Role.GM)

        assertNotNull(loginToken)
        assertTrue(loginToken.isNotEmpty())
        assertTrue(loginToken.contains(".")) // JWT should contain dots as separators
    }

    @Test
    fun `createLoginToken embeds the role, issuer, audience and a future expiration`() {
        val accountId = 789L

        val loginToken = jwtService.createLoginToken(accountId, Role.SUPER_GM)

        val claims = Jwts.parser()
            .verifyWith(Keys.hmacShaKeyFor(secret.toByteArray(StandardCharsets.UTF_8)))
            .build()
            .parseSignedClaims(loginToken)
            .payload

        assertEquals(accountId.toString(), claims.subject)
        assertEquals("login", claims.issuer)
        assertTrue(claims.audience.contains("zone"))
        assertEquals("SUPER_GM", claims.get("role", String::class.java))
        assertTrue(claims.expiration.after(Date()))
    }

    @Test
    fun `createLoginToken generates different tokens for different account IDs`() {
        val accountId1 = 123L
        val accountId2 = 124L

        val loginToken1 = jwtService.createLoginToken(accountId1, Role.USER)
        val loginToken2 = jwtService.createLoginToken(accountId2, Role.USER)

        assertNotEquals(loginToken1, loginToken2)
    }

    @Test
    fun `createLoginToken generates different tokens for different roles`() {
        val accountId = 123L

        val loginToken1 = jwtService.createLoginToken(accountId, Role.USER)
        val loginToken2 = jwtService.createLoginToken(accountId, Role.SUPER_GM)

        assertNotEquals(loginToken1, loginToken2)
    }

    /** The zone accepts each token once, by its id. */
    @Test
    fun `every login token carries its own id`() {
        val ids = (1..2).map {
            Jwts.parser().verifyWith(Keys.hmacShaKeyFor(secret.toByteArray(StandardCharsets.UTF_8))).build()
                .parseSignedClaims(jwtService.createLoginToken(1L, Role.USER)).payload.id
        }

        assertTrue(ids.all { !it.isNullOrBlank() })
        assertNotEquals(ids[0], ids[1])
    }

    @Test
    fun `an api token names its account`() {
        assertEquals(789L, jwtService.validateApiToken(jwtService.createApiToken(789L)))
    }

    /** Each token is meant for one receiver, so none of them may open another's door. */
    @Test
    fun `a zone login token is not an api token`() {
        assertNull(jwtService.validateApiToken(jwtService.createLoginToken(789L, Role.SUPER_GM)))
    }

    @Test
    fun `a service token is not an api token`() {
        assertNull(jwtService.validateApiToken(jwtService.createServiceToken(789L, ServiceTokens.KICK_SCOPE)))
    }

    @Test
    fun `a tampered api token is refused`() {
        val token = jwtService.createApiToken(789L)

        assertNull(jwtService.validateApiToken(token.dropLast(2) + "xx"))
    }
}
