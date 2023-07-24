package net.bestia.loginserver.login

import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/v1/login")
class LoginController(
    private val loginService: BasicLoginService
) {

  @PostMapping("/basic", consumes = [MediaType.APPLICATION_JSON_VALUE])
  fun basicCredentials(@RequestBody credentials: BasicCredentials): BestiaLoginToken {
    return loginService.login(credentials)
  }
}