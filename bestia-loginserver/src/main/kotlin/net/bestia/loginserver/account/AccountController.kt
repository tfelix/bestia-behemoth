package net.bestia.loginserver.account

import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("account")
class AccountController(
    private val accountCreateService: AccountCreateService
) {

  @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE])
  fun createAccount(@RequestBody accountCreate: AccountCreateModel) {
    accountCreateService.createAccount(accountCreate)
  }
}