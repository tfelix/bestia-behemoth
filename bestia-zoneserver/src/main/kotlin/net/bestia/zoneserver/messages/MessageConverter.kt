package net.bestia.zoneserver.messages

import net.bestia.messages.proto.MessageProtos

abstract class MessageConverter<T> {
  abstract fun convertToPayload(msg: T): ByteArray
  open fun convertToMessage(msg: MessageProtos.Wrapper): T {
    error("Conversion to Bestia Message not implemented for $fromMessage")
  }

  abstract val fromMessage: Class<T>
  abstract val fromPayload: MessageProtos.Wrapper.PayloadCase

  protected fun wrap(fn: (builder: MessageProtos.Wrapper.Builder) -> Unit): ByteArray {
    val b = MessageProtos.Wrapper.newBuilder()
    fn(b)
    return b.build().toByteArray()
  }
}