package net.bestia.zoneserver.messages

import java.lang.IllegalStateException

/**
 * This class does throw if there is a translation requested from client to the server.
 * Should be used as base converter class if only outgoing requests are wanted. E.g. it is
 * not desirable if the client is able to set a altered TemperatureComponent to the server.
 * This should only be send from the server to the client.
 */
abstract class OnlyToClientConverter<T> : MessageConverter<T>() {

  final override fun convertFromWire(data: ByteArray): T {
    throw IllegalStateException("Can not convert type ${canConvert.simpleName} coming from wire")
  }
}