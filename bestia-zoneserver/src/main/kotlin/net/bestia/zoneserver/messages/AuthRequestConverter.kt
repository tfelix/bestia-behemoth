package net.bestia.zoneserver.messages

import net.bestia.messages.proto.MessageProtos
import net.bestia.zoneserver.actor.socket.AuthRequest
import org.springframework.stereotype.Component

@Component
class AuthRequestConverter : MessageConverter<AuthRequest>() {
  override val fromMessage: Class<AuthRequest> = AuthRequest::class.java
  override val fromPayload: MessageProtos.Wrapper.PayloadCase = MessageProtos.Wrapper.PayloadCase.AUTH_REQUEST

  override fun convertToPayload(msg: AuthRequest): ByteArray {
    error("Sending AuthRequest to client is not supported")
  }

  override fun convertToMessage(msg: MessageProtos.Wrapper): AuthRequest {
    return AuthRequest(
        accountId = msg.authRequest.accountId,
        token = msg.authRequest.token
    )
  }
}