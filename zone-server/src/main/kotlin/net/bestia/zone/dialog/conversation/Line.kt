package net.bestia.zone.dialog.conversation

import net.bestia.zone.dialog.DialogArg

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
