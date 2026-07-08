package net.bestia.client.formatter

import net.bestia.bnet.proto.EnvelopeProto


class ChatFormatter : Formatter {
  override fun canHandle(envelope: EnvelopeProto.Envelope): Boolean {
    return envelope.hasChatSmsg()
  }

  override fun format(envelope: EnvelopeProto.Envelope): String {
    val chat = envelope.chatSmsg

    val mode = "[${chat.mode.name.uppercase()}]"
    val sender = if (chat.hasSenderName()) {
      "${chat.senderName}: "
    } else {
      ""
    }

    return "CHAT $mode $sender${chat.text}"
  }
}
