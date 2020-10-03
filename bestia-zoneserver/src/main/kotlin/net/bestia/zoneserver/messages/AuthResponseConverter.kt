package net.bestia.zoneserver.messages

import net.bestia.messages.proto.AccountProtos
import net.bestia.messages.proto.ComponentProtos
import net.bestia.messages.proto.MessageProtos
import net.bestia.zoneserver.actor.socket.AuthResponse
import net.bestia.zoneserver.actor.socket.LoginResponse
import net.bestia.zoneserver.entity.component.TemperatureComponent
import org.springframework.stereotype.Component

@Component
class AuthResponseConverter : MessageConverter<AuthResponse>() {
  override val fromMessage: Class<AuthResponse> = AuthResponse::class.java
  override val fromPayload: MessageProtos.Wrapper.PayloadCase = MessageProtos.Wrapper.PayloadCase.AUTH_RESPONSE

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
}