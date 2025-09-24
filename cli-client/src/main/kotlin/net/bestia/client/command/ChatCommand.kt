package net.bestia.client.command

import net.bestia.bnet.proto.ChatMessageProto
import net.bestia.bnet.proto.EnvelopeProto

class ChatCommand(
  private val session: Session
) : CliCommand {
  override val name = "chat"
  override val usage = "chat [public, party, guild, whisper:username] <chat text>"
  override val description = "Sends a chat"

  override fun execute(tokens: List<String>) {
    if (tokens.size < 3) {
      session.print("Usage: $usage")
      return
    }

    val mode = tokens[1].lowercase()
    val text = tokens.drop(2).joinToString(" ")

    val messageBuilder = ChatMessageProto.ChatMessage.newBuilder()
      .setText(text)

    if(mode == "public") {
      messageBuilder.setMode(ChatMessageProto.Mode.PUBLIC)
    } else if(mode == "party") {
      messageBuilder.setMode(ChatMessageProto.Mode.PARTY)
    } else if(mode == "guild") {
      messageBuilder.setMode(ChatMessageProto.Mode.GUILD)
    } else if(mode.startsWith("whisper:")) {
      val username = mode.substring("whisper:".length)
      if(username.isNotEmpty()) {
        messageBuilder.setMode(ChatMessageProto.Mode.WHISPER)
        messageBuilder.setTargetPlayerName(username)
      } else {
        session.print("Usage: $usage")
        return
      }
    } else {
      session.print("Usage: $usage")
      return
    }

    val envelope = EnvelopeProto.Envelope.newBuilder()
      .setChat(messageBuilder)
      .build()

    session.sendEnvelope(envelope)
  }
}