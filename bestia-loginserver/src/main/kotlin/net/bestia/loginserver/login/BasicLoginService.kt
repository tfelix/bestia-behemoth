package net.bestia.loginserver.login

import net.bestia.model.login.BasicLoginRepository
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.ZonedDateTime
import java.util.*

@Service
class BasicLoginService(
    private val basicLoginRepository: BasicLoginRepository
) {

  private val passwordEncoder = BCryptPasswordEncoder()

  fun login(credentials: BasicCredentials): BestiaToken {
    val basicLogin = basicLoginRepository.findByEmail(credentials.username)
        ?: throw AuthenticationException

    if (!passwordEncoder.matches(basicLogin.password, credentials.password)) {
      throw AuthenticationException
    }

    basicLogin.account.bannedUntil?.let {
      if (it > ZonedDateTime.now(Clock.systemUTC())) {
        throw BannedAuthenticationException(until = it)
      }
    }

    val token = BestiaToken(UUID.randomUUID().toString())
    basicLogin.account.loginToken = token.token
    basicLoginRepository.save(basicLogin)

    return token
  }
}