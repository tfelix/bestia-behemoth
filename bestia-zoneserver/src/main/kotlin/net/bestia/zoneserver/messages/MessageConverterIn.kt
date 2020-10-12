package net.bestia.zoneserver.messages

import net.bestia.messages.proto.MessageProtos

interface MessageConverterIn<T> {
  val fromPayload: MessageProtos.Wrapper.PayloadCase
  fun convertToMessage(accountId: Long, msg: MessageProtos.Wrapper): T
}