package net.bestia.zoneserver.messages

import net.bestia.messages.proto.MessageProtos

interface MessageConverterOut<T> {
  val fromMessage: Class<T>
  fun convertToPayload(msg: T): ByteArray

  fun wrap(fn: (builder: MessageProtos.Wrapper.Builder) -> Unit): ByteArray {
    val b = MessageProtos.Wrapper.newBuilder()
    fn(b)

    require(b.payloadCase != MessageProtos.Wrapper.PayloadCase.PAYLOAD_NOT_SET) {
      "No payload was set! Please call assign a Wrapper payload during the wrap callback."
    }

    return b.build().toByteArray()
  }
}