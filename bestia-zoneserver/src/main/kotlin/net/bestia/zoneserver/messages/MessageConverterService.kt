package net.bestia.zoneserver.messages

import com.google.protobuf.InvalidProtocolBufferException
import mu.KotlinLogging
import net.bestia.messages.proto.MessageProtos
import org.springframework.stereotype.Service
import java.nio.ByteBuffer

private val LOG = KotlinLogging.logger { }

@Service
class MessageConverterService(
    convertersIncoming: List<MessageConverterIn<*>>,
    convertersOutgoing: List<MessageConverterOut<*>>
) {

  @Suppress("UNCHECKED_CAST")
  private val toPayload = (convertersOutgoing as List<MessageConverterOut<Any>>)
      .map { it.fromMessage to it }
      .toMap()

  @Suppress("UNCHECKED_CAST")
  private val toMessage = (convertersIncoming as List<MessageConverterIn<Any>>)
      .map { it.fromPayload to it }
      .toMap()

  init {
    LOG.trace { "Registered message converter (out): $toPayload" }
    LOG.trace { "Registered message converter (in): $toMessage" }
  }

  fun convertToPayload(msg: Any): ByteArray {
    val foundConverter = toPayload[msg.javaClass]
        ?: error("Had no payload converter registered for ${msg.javaClass.simpleName}")

    val payload = foundConverter.convertToPayload(msg)

    LOG.trace { "Encoded from server: ${msg.javaClass.simpleName}" }

    val sendBuffer = ByteBuffer.allocate(payload.size + Int.SIZE_BYTES)
    sendBuffer.putInt(payload.size)
    sendBuffer.put(payload)

    return sendBuffer.array()
  }

  fun convertToMessage(accountId: Long, msg: ByteArray): Any? {
    try {
      val wrapper = MessageProtos.Wrapper.parseFrom(msg)

      LOG.debug { "Decoded from client: ${wrapper.payloadCase}" }

      val converter = toMessage[wrapper.payloadCase]
          ?: error("Had no message converter registered for ${wrapper.payloadCase}")

      return converter.convertToMessage(accountId, wrapper)
    } catch (e: InvalidProtocolBufferException) {
      throw MessageConvertException(e)
    }
  }
}