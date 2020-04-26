package net.bestia.zoneserver.messages

import mu.KotlinLogging
import net.bestia.messages.proto.Messages.Wrapper.PayloadCase
import net.bestia.messages.proto.Messages

private val LOG = KotlinLogging.logger { }

/**
 * Parses the Protobuf messages in objects which can then be used to
 * serialized the internally used classes from them.
 */
class ProtoMessageFactory {

  fun fromByteBuffer(buffer: ByteArray): Any? {
    val wrapper = Messages.Wrapper.parseFrom(buffer)

    return when (wrapper.payloadCase) {
      PayloadCase.AUTH -> wrapper.auth
      PayloadCase.PAYLOAD_NOT_SET -> {
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