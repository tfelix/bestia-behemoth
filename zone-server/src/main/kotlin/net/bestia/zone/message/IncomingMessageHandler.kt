package net.bestia.zone.message

import kotlin.reflect.KClass

/**
 * Handles one type of client message. Which kind it is decides the thread it runs on: a [TickMessageHandler]
 * on the tick thread, an [IoMessageHandler] on an IO thread. One message type has exactly one handler.
 */
sealed interface IncomingMessageHandler<T : CMSG> {
  /** The envelope case this handler answers, and how its message is read off the wire. */
  val wire: WireDecoder<T>

  val handles: KClass<T>
    get() = wire.type
}
