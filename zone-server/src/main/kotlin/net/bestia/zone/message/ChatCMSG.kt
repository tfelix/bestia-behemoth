package net.bestia.zone.message

import net.bestia.bnet.proto.ChatCmsgProto

data class ChatCMSG(
  override val playerId: Long,
  val type: Type,
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

    if (type == Type.COMMAND) {
      require(text.startsWith("/")) {
        "Text of type COMMAND must start with /"
      }
    }
  }

  enum class Type {
    PUBLIC,
    WHISPER,
    PARTY,
    GUILD,
    ERROR,
    COMMAND,
    GM,
    BROADCAST
  }

  companion object {
    fun fromBnet(
      accountId: Long,
      chat: ChatCmsgProto.ChatCMSG
    ): ChatCMSG {
      return ChatCMSG(
        playerId = accountId,
        type = when (chat.mode) {
          ChatCmsgProto.Mode.PARTY -> Type.PARTY
          ChatCmsgProto.Mode.GUILD -> Type.GUILD
          ChatCmsgProto.Mode.WHISPER -> Type.WHISPER
          ChatCmsgProto.Mode.PUBLIC -> Type.PUBLIC
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
      Type.WHISPER
    )

    private val TARGET_USERNAME_NOT_ALLOWED_TYPES = setOf(
      Type.PUBLIC,
      Type.PARTY,
      Type.GUILD,
      Type.ERROR,
      Type.BROADCAST
    )
  }
}