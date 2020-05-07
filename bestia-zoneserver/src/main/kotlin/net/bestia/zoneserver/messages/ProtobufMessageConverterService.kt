package net.bestia.zoneserver.messages

import mu.KotlinLogging
import net.bestia.messages.proto.Messages
import org.springframework.stereotype.Service
import java.lang.IllegalStateException

private val LOG = KotlinLogging.logger { }

@Service
class ProtobufMessageConverterService(
    existingConverters: List<MessageConverter<Any>>
) {

  private val fromBestiaConverter = existingConverters
      .map { it.canConvert to it }
      .toMap()

  fun fromBestia(msg: Any): ByteArray {
    val foundConverter = fromBestiaConverter[msg.javaClass]
        ?: throw IllegalStateException("Had no converter registered for ${msg.javaClass.simpleName}")

    return foundConverter.convertFromBestia(msg)
  }

  fun fromWire(msg: ByteArray): Any? {
    val wrapper = Messages.Wrapper.parseFrom(msg)

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