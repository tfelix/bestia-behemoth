package net.bestia.zoneserver.messages

import com.google.protobuf.InvalidProtocolBufferException
import mu.KotlinLogging
import net.bestia.messages.proto.MessageProtos
import org.springframework.stereotype.Service
import java.nio.ByteBuffer

private val LOG = KotlinLogging.logger { }

@Service
class MessageConverterService(
    existingConverters: List<MessageConverter<*>>
) {

  @Suppress("UNCHECKED_CAST")
  private val toPayload = (existingConverters as List<MessageConverter<Any>>)
      .map { it.fromMessage to it }
      .toMap()

  @Suppress("UNCHECKED_CAST")
  private val toMessage = (existingConverters as List<MessageConverter<Any>>)
      .map { it.fromPayload to it }
      .toMap()

  init {
    LOG.debug { "Registered message converter: $existingConverters" }
  }

  fun convertToPayload(msg: Any): ByteArray {
    val foundConverter = toPayload[msg.javaClass]
        ?: error("Had no payload converter registered for ${msg.javaClass.simpleName}")

    val payload = foundConverter.convertToPayload(msg)

    val sendBuffer = ByteBuffer.allocate(payload.size + Int.SIZE_BYTES)
    sendBuffer.putInt(payload.size)
    sendBuffer.put(payload)

    return sendBuffer.array()
  }

  fun convertToMessage(msg: ByteArray): Any? {
    try {
      val wrapper = MessageProtos.Wrapper.parseFrom(msg)

      val converter = toMessage[wrapper.payloadCase]
          ?: error("Had no message converter registered for ${wrapper.payloadCase}")

      return converter.convertToMessage(wrapper)
    } catch (e: InvalidProtocolBufferException) {
      throw MessageConvertException(e)
    }
  }
}