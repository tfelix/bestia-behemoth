package net.bestia.login.eip712

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/auth/eip712sig")
class Eip712AuthenticationController(
  private val loginService: Eip712AuthenticationService
) {
  data class AuthRequest(
    val wallet: String,
    val tokenIndex: Long,
    val signature: String,
  )

  data class Eip712AuthSuccess(
    val wallet: String,
    val tokenIndex: Long,
    val token: String
  )

  data class Eip712AuthFailure(
    val error: String
  )

  @PostMapping
  fun verifySignature(@RequestBody req: AuthRequest): ResponseEntity<*> {
    return when (val loginResult = loginService.authenticate(req)) {
      is Eip712AuthenticationService.AuthResult.Failure -> {
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(Eip712AuthFailure(loginResult.error))
      }

      is Eip712AuthenticationService.AuthResult.Success -> {
        ResponseEntity.ok(
          Eip712AuthSuccess(
            wallet = req.wallet,
            tokenIndex = req.tokenIndex,
            token = loginResult.jwtToken
          )
        )
      }
    }
  }
}