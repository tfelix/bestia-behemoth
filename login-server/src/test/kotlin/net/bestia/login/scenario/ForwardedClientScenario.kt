package net.bestia.login.scenario

import net.bestia.login.util.SecureTokens
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity

/**
 * Behind the TLS proxy every request arrives from the proxy's address. Keyed on that, one client using up its
 * login attempts would lock out every other player.
 */
class ForwardedClientScenario : BasePasskeyScenario() {

  @Test
  fun `two clients behind the proxy have a rate limit each`() {
    repeat(REQUESTS_PER_WINDOW) { startFrom(FIRST_CLIENT) }
    assertEquals(429, startFrom(FIRST_CLIENT).statusCode.value(), "the first client has used its window")

    assertNotEquals(429, startFrom(SECOND_CLIENT).statusCode.value())
  }

  private fun startFrom(clientAddress: String): ResponseEntity<String> {
    val headers = HttpHeaders().apply {
      contentType = MediaType.APPLICATION_JSON
      add("X-Forwarded-For", clientAddress)
    }
    val body = startBody("http://127.0.0.1:$LOOPBACK_PORT/callback", SecureTokens.randomToken())

    return restTemplate.postForEntity("/api/v1/auth/game/start", HttpEntity(body, headers), String::class.java)
  }

  private companion object {
    const val REQUESTS_PER_WINDOW = 20
    const val FIRST_CLIENT = "203.0.113.1"
    const val SECOND_CLIENT = "203.0.113.2"
  }
}
