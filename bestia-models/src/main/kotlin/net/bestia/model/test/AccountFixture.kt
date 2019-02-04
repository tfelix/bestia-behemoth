package net.bestia.model.test

import net.bestia.model.account.Account
import net.bestia.model.account.Gender
import java.time.Instant

object AccountFixture {

  const val email = "test@example.com"
  const val username = "usernamer"
  val registeredOn = Instant.parse("2018-10-12T22:27:28.558Z")!!
  const val password = "sample123"

  @JvmStatic
  fun createAccount(): Account {
    return Account(
        email = email,
        username = username,
        registerDate = registeredOn,
        password = password,
        gender = Gender.MALE
    )
  }
}