package net.bestia.loginserver.login

import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("login")
class LoginController {

  @PostMapping(
      path = ["/basic"],
      consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE]
  )
  fun basicCredentials(@RequestBody credentials: BasicCredentials): BestiaToken {
    return BestiaToken(UUID.randomUUID().toString())
  }
}