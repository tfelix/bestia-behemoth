package net.bestia.zoneserver.messages

import net.bestia.messages.proto.MessageProtos
import net.bestia.zoneserver.actor.socket.AuthRequest
import org.springframework.stereotype.Component

@Component
class AuthRequestConverter : MessageConverterIn<AuthRequest> {

  /**
   * This is a bit special one, the account id here is provided via the message and not
   * with the external parameter.I
   */
  override fun convertToMessage(accountId: Long, msg: MessageProtos.Wrapper): AuthRequest {
    return AuthRequest(
        accountId = msg.authRequest.accountId,
        token = msg.authRequest.token
    )
  }

  override val fromPayload: MessageProtos.Wrapper.PayloadCase = MessageProtos.Wrapper.PayloadCase.AUTH_REQUEST
}