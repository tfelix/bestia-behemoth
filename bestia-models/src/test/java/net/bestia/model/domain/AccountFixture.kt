package net.bestia.model.domain

import java.time.Instant

object AccountFixture {

  val email = "test@example.com"
  val username = "usernamer"
  val registeredOn = Instant.parse("2018-10-12")
  val password = Password("sample123")

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