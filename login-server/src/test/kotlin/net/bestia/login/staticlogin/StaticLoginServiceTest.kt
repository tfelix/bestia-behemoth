package net.bestia.login.staticlogin

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import net.bestia.account.Role
import net.bestia.login.account.Account
import net.bestia.login.account.AccountRepository
import net.bestia.login.account.loginmethod.StaticTokenLoginMethod
import net.bestia.login.account.loginmethod.StaticTokenLoginMethodRepository
import net.bestia.login.jwt.JwtConfig
import net.bestia.login.jwt.JwtService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.nio.charset.StandardCharsets

@ExtendWith(MockKExtension::class)
class StaticLoginServiceTest {

  private val secret = "test-secret-key-that-is-long-enough-for-hmac-sha256-algorithm-requirements"

  @MockK
  private lateinit var staticTokenLoginMethodRepository: StaticTokenLoginMethodRepository

  @MockK
  private lateinit var accountRepository: AccountRepository

  private lateinit var sut: StaticLoginService

  private fun loginMethod(username: String, token: String, role: Role) =
    StaticTokenLoginMethod(
      account = Account(role = role),
      username = username,
      staticToken = token
    )

  @BeforeEach
  fun setUp() {
    val jwtService = JwtService(JwtConfig(secret = secret, expirationDays = 5, loginTokenMinutes = 60))
    every { accountRepository.save<Account>(any()) } answers { firstArg() }
    every { staticTokenLoginMethodRepository.save<StaticTokenLoginMethod>(any()) } answers { firstArg() }
    sut = StaticLoginService(staticTokenLoginMethodRepository, accountRepository, jwtService)
  }

  @Test
  fun `authenticate with correct credentials issues a login token carrying the role`() {
    every { staticTokenLoginMethodRepository.findByUsername("admin") } returns
      loginMethod("admin", "dev-admin-token", Role.SUPER_GM)

    val result = sut.authenticate("admin", "dev-admin-token")

    assertTrue(result is StaticLoginService.AuthResult.Success)
    val claims = Jwts.parser()
      .verifyWith(Keys.hmacShaKeyFor(secret.toByteArray(StandardCharsets.UTF_8)))
      .build()
      .parseSignedClaims((result as StaticLoginService.AuthResult.Success).jwtToken)
      .payload
    assertEquals("login", claims.issuer)
    assertTrue(claims.audience.contains("zone"))
    assertEquals("SUPER_GM", claims.get("role", String::class.java))
  }

  @Test
  fun `authenticate with wrong token fails`() {
    every { staticTokenLoginMethodRepository.findByUsername("admin") } returns
      loginMethod("admin", "dev-admin-token", Role.SUPER_GM)

    val result = sut.authenticate("admin", "wrong-token")

    assertTrue(result is StaticLoginService.AuthResult.Failure)
  }

  @Test
  fun `authenticate with unknown user fails`() {
    every { staticTokenLoginMethodRepository.findByUsername("ghost") } returns null

    val result = sut.authenticate("ghost", "whatever")

    assertTrue(result is StaticLoginService.AuthResult.Failure)
  }
}
