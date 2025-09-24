package net.bestia.zone.message

/**
 * Marker interface to make clear this message is an incoming message that is processed by the server via the
 * eventing system.
 */
interface CMSG {
  val playerId: Long
}