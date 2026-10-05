package net.bestia.zone.message

/**
 * Runs on an IO thread, for messages that need the database. It reaches the world only through a
 * [net.bestia.zone.ecs.core.WorldView] scope.
 */
interface IoMessageHandler<T : CMSG> : IncomingMessageHandler<T> {
  /** Returns whether the message was handled. */
  fun handle(msg: T): Boolean
}
