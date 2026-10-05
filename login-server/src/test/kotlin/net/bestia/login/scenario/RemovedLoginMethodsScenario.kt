package net.bestia.login.scenario

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType

/**
 * The wallet login picked the account by a client-supplied NFT token id and signed no nonce, so anybody could
 * sign in as any NFT holder and replay a signature forever. Passkeys are the only way in.
 */
class RemovedLoginMethodsScenario : BaseLoginScenario() {

  @Autowired
  private lateinit var restTemplate: TestRestTemplate

  @Test
  fun `the wallet signature login is gone`() {
    val body = mapOf("wallet" to "0x0000000000000000000000000000000000000001", "tokenIndex" to 1, "signature" to "0x00")

    assertEquals(HttpStatus.NOT_FOUND, post("/api/v1/auth/eip712sig", body))
  }

  @Test
  fun `the refresh token login it fed is gone`() {
    assertEquals(HttpStatus.NOT_FOUND, post("/api/v1/login", mapOf("refreshToken" to "anything")))
  }

  private fun post(path: String, body: Any): HttpStatus {
    val headers = HttpHeaders()
    headers.contentType = MediaType.APPLICATION_JSON

    val response = restTemplate.postForEntity(path, HttpEntity(body, headers), String::class.java)

    return HttpStatus.valueOf(response.statusCode.value())
  }
}
