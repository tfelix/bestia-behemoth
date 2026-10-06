package net.bestia.zone.chat.net

import net.bestia.bnet.proto.ChatCmsgProto
import net.bestia.zone.message.CMSG
import net.bestia.zone.chat.ChatType

data class ChatCMSG(
  override val playerId: Long,
  val type: ChatType,
  val text: String,
  val targetUsername: String? = null
) : CMSG {

  init {
    if (TARGET_USERNAME_REQUIRED_TYPES.contains(type)) {
      requireNotNull(targetUsername) { "targetUsername must not be null if chat is $type" }
    }

    if (TARGET_USERNAME_NOT_ALLOWED_TYPES.contains(type)) {
      require(targetUsername == null) {
        "targetUsername is not allowed for type $type"
      }
    }

    if (type == ChatType.COMMAND) {
      require(text.startsWith("/")) {
        "Text of type COMMAND must start with /"
      }
    }
  }

  companion object {
    fun fromBnet(
      accountId: Long,
      chat: ChatCmsgProto.ChatCMSG
    ): ChatCMSG {
      return ChatCMSG(
        playerId = accountId,
        type = when (chat.mode) {
          ChatCmsgProto.Mode.PARTY -> ChatType.PARTY
          ChatCmsgProto.Mode.GUILD -> ChatType.GUILD
          ChatCmsgProto.Mode.WHISPER -> ChatType.WHISPER
          ChatCmsgProto.Mode.PUBLIC -> ChatType.PUBLIC
          ChatCmsgProto.Mode.COMMAND -> ChatType.COMMAND
          else -> throw IllegalStateException("Unknown chat mode: ${chat.mode}")
        },
        text = chat.text,
        targetUsername = if (chat.mode == ChatCmsgProto.Mode.WHISPER) {
          chat.targetPlayerName
        } else {
          null
        }
      )
    }

    private val TARGET_USERNAME_REQUIRED_TYPES = setOf(
      ChatType.WHISPER
    )

    private val TARGET_USERNAME_NOT_ALLOWED_TYPES = setOf(
      ChatType.PUBLIC,
      ChatType.PARTY,
      ChatType.GUILD,
      ChatType.ERROR,
      ChatType.BROADCAST
    )
  }
}