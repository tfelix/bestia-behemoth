package net.bestia.zoneserver.messages

import net.bestia.messages.proto.AccountProtos
import net.bestia.messages.proto.MessageProtos
import net.bestia.zoneserver.actor.socket.AuthResponse
import net.bestia.zoneserver.actor.socket.LoginResponse
import org.springframework.stereotype.Component

@Component
class AuthResponseConverter : MessageConverterOut<AuthResponse> {

  override fun convertToPayload(msg: AuthResponse): ByteArray {
    val authResponseBuilder = AccountProtos.AuthResponse.newBuilder()
        .setAccountId(msg.accountId)

    authResponseBuilder.loginStatus = when (msg.response) {
      LoginResponse.SUCCESS -> AccountProtos.LoginStatus.SUCCESS
      LoginResponse.UNAUTHORIZED -> AccountProtos.LoginStatus.UNAUTHORIZED
      LoginResponse.NO_LOGINS_ALLOWED -> AccountProtos.LoginStatus.NO_LOGINS_ALLOWED
    }

    return wrap { it.authResponse = authResponseBuilder.build() }
  }

  override val fromMessage = AuthResponse::class.java
}