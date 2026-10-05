package net.bestia.zone.message

import kotlin.reflect.KClass

/**
 * Handles one type of client message. Which kind it is decides the thread it runs on: a [TickMessageHandler]
 * on the tick thread, an [IoMessageHandler] on an IO thread. One message type has exactly one handler.
 */
sealed interface IncomingMessageHandler<T : CMSG> {
  val handles: KClass<T>
}
