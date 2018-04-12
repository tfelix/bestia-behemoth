package net.bestia.messages

import akka.actor.ActorRef
import java.io.Serializable

/**
 * This is the central interface for any external component like services or
 * components to interact with the akka system.
 *
 * @author Thomas Felix
 */
interface MessageApi {

  /**
   * The message is send towards the client.
   *
   * @param message The message to be send to the client.
   */
  fun sendToClient(clientAccountId: Long, message: Serializable)

  /**
   * Sends a message directly to the entity actor managing a single entity
   * inside the cluster.
   *
   * @param message The message is directed towards an actor managing the entity.
   */
  fun sendToEntity(entityId: Long, message: Serializable)

  /**
   * Sets the central post message router for message digestion.
   */
  fun setPostmaster(postmaster: ActorRef)
}
