package net.bestia.zone.text

import net.bestia.bnet.proto.TextProto

/**
 * One text, as a translation key and its arguments.
 *
 * Never a sentence. The server does not know any language, and the moment it composes one the text stops
 * being translatable.
 *
 * @param textId a text written for this world, which the client loads in its own language and shows in place
 *   of the template behind [key]
 */
data class TextRef(
  val key: String,
  val args: Map<String, TextArg> = emptyMap(),
  val textId: String? = null,
)

fun TextRef.toBnet(): TextProto.TextRef {
  val ref = TextProto.TextRef.newBuilder().setKey(key)
  args.forEach { (name, arg) -> ref.putArgs(name, arg.toBnet()) }
  textId?.let { ref.setTextId(it) }

  return ref.build()
}
