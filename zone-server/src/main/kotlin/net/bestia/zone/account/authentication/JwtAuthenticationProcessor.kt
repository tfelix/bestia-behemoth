package net.bestia.zone.account.authentication

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.account.Authority
import net.bestia.zone.account.authentication.LoginTokenValidator
import net.bestia.bnet.proto.EnvelopeProto
import org.springframework.stereotype.Component

@Component
class JwtAuthenticationProcessor(
  private val loginTokenValidator: LoginTokenValidator
) : AuthenticationProcessor {

  private data class AuthData(
    val accountId: Long,
    val authorities: Set<Authority>
  )

  override fun authenticate(msg: EnvelopeProto.Envelope): AuthenticationProcessor.Authentication {
    val authRequest = msg.authentication
      ?: return AuthenticationProcessor.AuthenticationFailed

    val jwtToken = authRequest.token

    LOG.trace { "Authenticating token: $jwtToken" }

    val data = try {
      validateAndExtract(jwtToken)
    } catch (e: Exception) {
      LOG.debug(e) { "Authentication failed." }
      return AuthenticationProcessor.AuthenticationFailed
    }

    LOG.trace { "Authentication data: $data" }

    return AuthenticationProcessor.AuthenticationSuccess(
      accountId = data.accountId,
      authorities = data.authorities
    )
  }

  private fun validateAndExtract(jwtToken: String): AuthData {
    val claims = loginTokenValidator.validateLoginToken(jwtToken)

    return AuthData(
      accountId = claims.accountId,
      authorities = claims.authorities
    )
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
