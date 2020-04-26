package net.bestia.zoneserver.messages

import mu.KotlinLogging
import net.bestia.messages.proto.Messages
import org.springframework.stereotype.Service
import java.lang.IllegalStateException

private val LOG = KotlinLogging.logger { }

@Service
class MessageConverterService(
    existingConverters: List<MessageConverter<Any>>
) {

  private val fromBestiaConverter = existingConverters
      .map { it.canConvert to it }
      .toMap()

  fun fromBestia(msg: Any): Messages.Wrapper {
    val foundConverter = fromBestiaConverter[msg.javaClass]
        ?: throw IllegalStateException("Had no converter registered for ${msg.javaClass.simpleName}")

    return foundConverter.convertFromBestia(msg)
  }

  fun fromWire(msg: Messages.Wrapper): Any {
    val foundConverter = fromWireConverter[msg.javaClass]
        ?: throw IllegalStateException("Had no converter registered for ${msg.javaClass.simpleName}")

    return foundConverter.convertFromWire(msg)
  }

  fun fromByteBuffer(buffer: ByteArray): Any? {
    val wrapper = Messages.Wrapper.parseFrom(buffer)

    return when (wrapper.payloadCase) {
      Messages.Wrapper.PayloadCase.AUTH -> wrapper.auth
      Messages.Wrapper.PayloadCase.PAYLOAD_NOT_SET -> {
        LOG.warn { "No payload present in parsed message" }
        null
      }
      else -> {
        LOG.warn { "Unknown payload '${wrapper.payloadCase}' present in parsed message" }
        null
      }
    }
  }
}