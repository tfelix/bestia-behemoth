package net.bestia.zone.text

import net.bestia.bnet.proto.ConversationSmsgProto

/**
 * One text, as a translation key and its arguments.
 *
 * Never a sentence. The server does not know any language, and the moment it composes one the text stops
 * being translatable.
 */
data class TextRef(
  val key: String,
  val args: Map<String, TextArg> = emptyMap(),
)

/** Shared by every message that carries text: a conversation, and the homes offered to a new master. */
fun TextRef.toBnet(): ConversationSmsgProto.Line {
  val line = ConversationSmsgProto.Line.newBuilder().setKey(key)
  args.forEach { (name, arg) -> line.putArgs(name, arg.toBnet()) }

  return line.build()
}
