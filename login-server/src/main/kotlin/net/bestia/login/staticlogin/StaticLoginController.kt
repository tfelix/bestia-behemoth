package net.bestia.login.staticlogin

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth/static")
class StaticLoginController(
  private val staticLoginService: StaticLoginService
) {
  data class StaticAuthRequest(
    val username: String,
    val token: String
  )

  data class StaticAuthSuccess(
    val token: String
  )

  data class StaticAuthFailure(
    val error: String
  )

  @PostMapping
  fun login(@RequestBody req: StaticAuthRequest): ResponseEntity<*> {
    return when (val result = staticLoginService.authenticate(req.username, req.token)) {
      is StaticLoginService.AuthResult.Success ->
        ResponseEntity.ok(StaticAuthSuccess(token = result.jwtToken))

      is StaticLoginService.AuthResult.Failure ->
        ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(StaticAuthFailure(result.error))
    }
  }
}
