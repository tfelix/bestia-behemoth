package net.bestia.loginserver.login

import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("login/basic")
class LoginController(
    private val loginService: BasicLoginService
) {

  @PostMapping(consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE])
  fun basicCredentials(@RequestBody credentials: BasicCredentials): BestiaToken {
    return loginService.login(credentials)
  }
}