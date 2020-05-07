package net.bestia.loginserver.login

import net.bestia.model.account.AccountRepository
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service

@Service
class BasicLoginService(
    private val accountRepository: AccountRepository
) {

  private val passwordEncoder = BCryptPasswordEncoder()

  fun login(credentials: BasicCredentials) {

    val savedPassword = ""
    if (!passwordEncoder.matches(savedPassword, credentials.password)) {
      throw AuthenticationException
    }
  }
}