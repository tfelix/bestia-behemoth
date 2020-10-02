package net.bestia.zoneserver.messages

import mu.KotlinLogging
import net.bestia.messages.proto.MessageProtos
import org.springframework.stereotype.Service

private val LOG = KotlinLogging.logger { }

@Service
class ProtobufMessageConverterService(
    existingConverters: List<MessageConverter<Any>>
) {

  private val toPayload = existingConverters
      .map { it.fromMessage to it }
      .toMap()

  private val toMessage = existingConverters
      .map { it.fromPayload to it }
      .toMap()

  init {
    LOG.debug { "Registered message converter: $existingConverters" }
  }

  fun convertToPayload(msg: Any): ByteArray {
    val foundConverter = toPayload[msg.javaClass]
        ?: error("Had no payload converter registered for ${msg.javaClass.simpleName}")

    return foundConverter.convertToPayload(msg)
  }

  fun convertToMessage(msg: ByteArray): Any? {
    val wrapper = MessageProtos.Wrapper.parseFrom(msg)

    val converter = toMessage[wrapper.payloadCase]
        ?: error("Had no message converter registered for ${msg.javaClass.simpleName}")

    return converter.convertToMessage(wrapper)
  }
}