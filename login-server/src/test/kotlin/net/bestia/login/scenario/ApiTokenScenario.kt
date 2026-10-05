package net.bestia.login.scenario

import net.bestia.login.jwt.JwtService
import net.bestia.login.webauthn.VirtualAuthenticator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

/** A game client calls the login server's REST API with a short-lived token of its own, never its zone token. */
class ApiTokenScenario : BasePasskeyScenario() {

  @Autowired
  private lateinit var jwtService: JwtService

  @Test
  fun `a login hands out an api token for the account`() {
    val owner = register(VirtualAuthenticator())

    val body = mapper.readTree(rawExchange(owner.code, owner.verifier).body)

    assertEquals(accountIdOf(body.get("token").asText()), jwtService.validateApiToken(body.get("api_token").asText()))
  }

  @Test
  fun `a refresh hands out a fresh api token`() {
    val owner = register(VirtualAuthenticator())
    val tokens = exchangeFully(owner.code, owner.verifier)

    val body = mapper.readTree(rawRefresh(tokens.refreshToken).body)

    assertEquals(accountIdOf(tokens.token), jwtService.validateApiToken(body.get("api_token").asText()))
  }
}
