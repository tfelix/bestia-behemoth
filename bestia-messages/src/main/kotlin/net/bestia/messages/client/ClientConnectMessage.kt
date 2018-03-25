package net.bestia.messages.client

import akka.actor.ActorRef
import net.bestia.messages.AccountMessage

/**
 * This message is send by the webserver frontend as soon as a client is fully
 * connected and must be registered into the bestia system. As soon as this
 * message arrives the client is authenticated and connected and must/can
 * receive messages from now on.
 *
 * @author Thomas Felix
 */
class ClientConnectMessage(
        accId: Long,
        val state: ConnectionState,
        /**
         * Webserver who did send this message and to which the client
         * mentioned in this message is connected.
         */
        val webserverRef: ActorRef
) : AccountMessage(accId) {

  enum class ConnectionState {
    CONNECTED, DISCONNECTED, UNKNOWN
  }

  override fun toString(): String {
    return "ClientConnectMessage[accId: $accountId, status: $state]"
  }

  override fun createNewInstance(accountId: Long): ClientConnectMessage {
    return ClientConnectMessage(accountId, this.state, this.webserverRef)
  }
}