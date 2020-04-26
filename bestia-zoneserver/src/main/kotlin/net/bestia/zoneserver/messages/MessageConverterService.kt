package net.bestia.zoneserver.messages

import net.bestia.messages.proto.Messages
import org.springframework.stereotype.Service
import java.lang.IllegalStateException

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
}