package net.bestia.zone.account.authentication

import net.bestia.bnet.proto.EnvelopeProto

interface AuthenticationProcessor {

  sealed class Authentication
  data object AuthenticationFailed : Authentication()
  data class AuthenticationSuccess(
    val accountId: Long,
  ) : Authentication()

  fun authenticate(msg: EnvelopeProto.Envelope): Authentication
}
