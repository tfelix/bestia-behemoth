package net.bestia.zoneserver.messages

import net.bestia.messages.proto.Messages

abstract class MessageConverter<T> {
  abstract fun convertFromWire(proto: Messages.Wrapper): T
  abstract fun convertFromBestia(msg: T): Messages.Wrapper
  abstract val canConvert: Class<T>
}