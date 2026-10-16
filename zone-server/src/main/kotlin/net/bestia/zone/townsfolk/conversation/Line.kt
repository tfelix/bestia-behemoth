package net.bestia.zone.townsfolk.conversation

import net.bestia.bnet.proto.ConversationSmsgProto
import net.bestia.zone.dialog.DialogArg
import net.bestia.zone.dialog.toBnet

/**
 * One thing said, as a translation key and its arguments.
 *
 * Never a sentence. The server does not know any language, and the moment it composes one the line stops
 * being translatable - which is the whole constraint the dialogue design is built around.
 */
data class Line(
  val key: String,
  val args: Map<String, DialogArg> = emptyMap(),
)

/** Shared by every message that carries lines: a conversation, and the homes offered to a new master. */
fun Line.toBnet(): ConversationSmsgProto.Line {
  val line = ConversationSmsgProto.Line.newBuilder().setKey(key)
  args.forEach { (name, arg) -> line.putArgs(name, arg.toBnet()) }

  return line.build()
}
