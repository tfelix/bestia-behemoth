package net.bestia.client.formatter

import net.bestia.bnet.proto.EnvelopeProto


class ChatFormatter : Formatter {
  override fun canHandle(envelope: EnvelopeProto.Envelope): Boolean {
    return envelope.hasChat()
  }

  override fun format(envelope: EnvelopeProto.Envelope): String {
    val chat = envelope.chat

    val mode = "[${chat.mode.name.uppercase()}]"
    val username = if (chat.hasTargetPlayerName()) {
      "${chat.targetPlayerName}: "
    } else {
      ""
    }

    return "CHAT $mode $username${chat.text}"
  }
}