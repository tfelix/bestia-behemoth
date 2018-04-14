package net.bestia.messages

import akka.actor.ActorRef

/**
 * This is the central interface for any external component like services or
 * components to interact with the akka system.
 *
 * @author Thomas Felix
 */
interface MessageApi {

  /**
   * Sends a message to the post master. Depending on the envelope this message is routed to
   * the apropriate receiver.
   */
  fun send(message: Any)

  /**
   * Sets the central post message router for message digestion.
   */
  fun setPostmaster(postmaster: ActorRef)
}
