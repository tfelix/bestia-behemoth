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
   * Sends the message to all active player bestias in update range. The given
   * message must refer to an entity source which has a attached. Otherwise the message origin position
   * can not be determined and thus no updates send to players.
   *
   * @param entityIdWithPosition The entity will be used as starting point to
   * determine all receiving entities. It therefore must have an position
   * component attached.
   * @param message The message to send to all active players inside the update
   * range.
   */
  fun sendToActiveClientsInRange(entityIdWithPosition: Long, message: Serializable)

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
