package net.bestia.zoneserver.messages

import net.bestia.messages.proto.MessageProtos

interface MessageConverter<T> {
  fun convertToPayload(msg: T): ByteArray
  fun convertToMessage(msg: MessageProtos.Wrapper): T

  val fromMessage: Class<T>
  val fromPayload: MessageProtos.Wrapper.PayloadCase
}