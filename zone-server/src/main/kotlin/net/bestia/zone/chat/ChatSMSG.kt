package net.bestia.zone.chat

import net.bestia.bnet.proto.ChatCmsgProto
import net.bestia.bnet.proto.ChatSmsgProto
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.zone.message.SMSG
import java.lang.IllegalStateException

data class ChatSMSG(
  val type: ChatType,
  val text: String,
  val senderUsername: String? = null,
  val senderEntityId: Long? = null
) : SMSG {

  init {
    if (USERNAME_REQUIRED_TYPES.contains(type)) {
      requireNotNull(senderUsername)
    }
  }

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val mode = when (type) {
      ChatType.PUBLIC -> ChatCmsgProto.Mode.PUBLIC
      ChatType.WHISPER -> ChatCmsgProto.Mode.WHISPER
      ChatType.PARTY -> ChatCmsgProto.Mode.PARTY
      ChatType.GUILD -> ChatCmsgProto.Mode.GUILD
      ChatType.ERROR -> ChatCmsgProto.Mode.ERROR
      ChatType.GM -> ChatCmsgProto.Mode.GM
      ChatType.BROADCAST -> ChatCmsgProto.Mode.BROADCAST
      ChatType.COMMAND -> ChatCmsgProto.Mode.COMMAND
    }

    val chat = ChatSmsgProto.ChatSMSG.newBuilder()
      .setMode(mode)
      .setText(text)

    if (senderUsername != null) {
      chat.setSenderName(senderUsername)
    }

    if (senderEntityId != null) {
      chat.setSenderEntityId(senderEntityId)
    }

    return EnvelopeProto.Envelope.newBuilder()
      .setChatSmsg(chat)
      .build()
  }

  companion object {
    private val USERNAME_REQUIRED_TYPES = setOf(
      ChatType.PUBLIC,
      ChatType.WHISPER,
      ChatType.PARTY,
      ChatType.GUILD,
      ChatType.GM
    )

    val ERROR_NO_PARTY = ChatSMSG(
      text = "error.no_party",
      type = ChatType.ERROR,
    )

    val ERROR_NOT_SUPPORTED = ChatSMSG(
      text = "error.not_supported",
      type = ChatType.ERROR,
    )
  }
}