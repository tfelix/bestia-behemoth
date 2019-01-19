package net.bestia.messages.client

import akka.actor.ActorRef
import java.io.Serializable

/**
 * This message is send by the webserver frontend as soon as a client is fully
 * connected and must be registered into the Bestia system. As soon as this
 * message arrives the client is authenticated and connected and must/can
 * receive messages from now on.
 *
 * @author Thomas Felix
 */
data class ClientConnectMessage(
        val accountId: Long,
        /**
         * Webserver who did send this message and to which the client
         * mentioned in this message is connected.
         */
        val webserverRef: ActorRef
) : Serializable

