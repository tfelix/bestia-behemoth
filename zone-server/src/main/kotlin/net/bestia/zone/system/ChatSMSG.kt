package net.bestia.zone.system

import net.bestia.bnet.proto.ChatCmsgProto
import net.bestia.bnet.proto.ChatSmsgProto
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.zone.message.SMSG
import java.lang.IllegalStateException

data class ChatSMSG(
  val type: ChatCMSG.Type,
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
      ChatCMSG.Type.PUBLIC -> ChatCmsgProto.Mode.PUBLIC
      ChatCMSG.Type.WHISPER -> ChatCmsgProto.Mode.WHISPER
      ChatCMSG.Type.PARTY -> ChatCmsgProto.Mode.PARTY
      ChatCMSG.Type.GUILD -> ChatCmsgProto.Mode.GUILD
      ChatCMSG.Type.ERROR -> ChatCmsgProto.Mode.ERROR
      ChatCMSG.Type.GM -> ChatCmsgProto.Mode.GM
      ChatCMSG.Type.BROADCAST -> ChatCmsgProto.Mode.BROADCAST
      ChatCMSG.Type.COMMAND -> {
        throw IllegalStateException("No bnet representation for type $type")
      }
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
      ChatCMSG.Type.PUBLIC,
      ChatCMSG.Type.WHISPER,
      ChatCMSG.Type.PARTY,
      ChatCMSG.Type.GUILD,
      ChatCMSG.Type.GM
    )

    val ERROR_UNKNOWN_USER = ChatSMSG(
      text = "error.player_not_found",
      type = ChatCMSG.Type.ERROR,
    )

    val ERROR_NO_PARTY = ChatSMSG(
      text = "error.no_party",
      type = ChatCMSG.Type.ERROR,
    )

    val ERROR_NOT_SUPPORTED = ChatSMSG(
      text = "error.not_supported",
      type = ChatCMSG.Type.ERROR,
    )
  }
}