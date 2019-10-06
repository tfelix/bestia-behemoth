package net.bestia.zoneserver.account

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class AccountServiceIT: AbstractIT() {

  @Autowired
  private lateinit var sut: AccountService

  @Test
  fun test() {
    println(sut)
  }
}