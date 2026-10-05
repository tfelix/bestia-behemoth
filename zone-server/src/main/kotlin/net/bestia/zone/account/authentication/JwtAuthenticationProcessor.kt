package net.bestia.zone.account.authentication

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.account.Authority
import net.bestia.bnet.proto.EnvelopeProto
import org.springframework.stereotype.Component

@Component
class JwtAuthenticationProcessor(
  private val loginTokenValidator: LoginTokenValidator,
  private val kickedAccounts: KickedAccounts
) : AuthenticationProcessor {

  private data class AuthData(
    val accountId: Long,
    val authorities: Set<Authority>,
    val kickedSince: Boolean
  )

  override fun authenticate(msg: EnvelopeProto.Envelope): AuthenticationProcessor.Authentication {
    val authRequest = msg.authentication
      ?: return AuthenticationProcessor.AuthenticationFailed

    // Never logged: the token is a bearer credential until it expires.
    val jwtToken = authRequest.token

    val data = try {
      validateAndExtract(jwtToken)
    } catch (e: Exception) {
      LOG.debug(e) { "Authentication failed." }
      return AuthenticationProcessor.AuthenticationFailed
    }

    if (data.kickedSince) {
      LOG.info { "Refused a login token for account ${data.accountId} that was issued before its kick" }
      return AuthenticationProcessor.AuthenticationFailed
    }

    LOG.debug { "Authenticated account ${data.accountId}" }

    return AuthenticationProcessor.AuthenticationSuccess(
      accountId = data.accountId,
      authorities = data.authorities
    )
  }

  private fun validateAndExtract(jwtToken: String): AuthData {
    val claims = loginTokenValidator.validateLoginToken(jwtToken)

    return AuthData(
      accountId = claims.accountId,
      authorities = claims.authorities,
      kickedSince = kickedAccounts.refuses(claims.accountId, claims.issuedAt)
    )
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
