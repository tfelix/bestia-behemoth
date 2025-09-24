package net.bestia.login.scenario

import net.bestia.login.LoginController
import net.bestia.login.eip712.Eip712AuthenticationController
import net.bestia.login.eip712.Eip712SignatureFixture
import net.bestia.login.eip712.Eip712Verifier.LoginPayload
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType

class BaseLoginE2EScenario : BaseLoginScenario() {

  @Autowired
  private lateinit var restTemplate: TestRestTemplate

  private val sigFixture = Eip712SignatureFixture()

  @Test
  fun `login flow works`() {
    // Step 1: Generate EIP-712 signature
    val tokenIndex = 123L
    val signature = sigFixture.generateValidSignature(LoginPayload(sigFixture.address, tokenIndex))

    // Step 2: POST to /api/v1/auth/eip712sig
    val authReq = mapOf(
      "wallet" to sigFixture.address,
      "tokenIndex" to tokenIndex,
      "signature" to signature
    )
    val authHeaders = HttpHeaders()
    authHeaders.contentType = MediaType.APPLICATION_JSON

    val authResp = restTemplate.postForEntity(
      "/api/v1/auth/eip712sig",
      HttpEntity(authReq, authHeaders),
      Eip712AuthenticationController.Eip712AuthSuccess::class.java
    )

    assertEquals(200, authResp.statusCode.value())

    val authResponse = authResp?.body

    assertNotNull(authResponse)

    val jwtToken = authResponse!!.token

    // TODO verify JWT token properly? Expiry date?

    assertEquals(sigFixture.address, authResponse.wallet)
    assertEquals(tokenIndex, authResponse.tokenIndex)

    // Step 3: POST to /api/v1/login with JWT as refreshToken
    val loginHeaders = HttpHeaders().apply {
      contentType = MediaType.APPLICATION_JSON
    }
    val loginResp = restTemplate.postForEntity(
      "/api/v1/login",
      HttpEntity(LoginController.LoginRequest(jwtToken), loginHeaders),
      LoginController.LoginSuccess::class.java
    )

    assertEquals(200, loginResp.statusCode.value())

    val loginJwt = loginResp?.body?.token

    assertNotNull(loginJwt)
    // TODO verify JWT token properly?
  }
}