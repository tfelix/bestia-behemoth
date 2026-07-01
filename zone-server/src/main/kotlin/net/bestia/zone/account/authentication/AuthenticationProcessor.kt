package net.bestia.zone.account.authentication

import net.bestia.account.Authority
import net.bestia.bnet.proto.EnvelopeProto

interface AuthenticationProcessor {

  sealed class Authentication
  data object AuthenticationFailed : Authentication()
  data class AuthenticationSuccess(
    val accountId: Long,
    val authorities: Set<Authority>,
  ) : Authentication()

  fun authenticate(msg: EnvelopeProto.Envelope): Authentication
}
