package net.bestia.messages;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import net.bestia.messages.EntityJsonMessage;
import net.bestia.messages.JsonMessage;

/**
 * This is the central interface for any external component like services or
 * components to interact with the akka system.
 * 
 * @author Thomas Felix
 *
 */
public interface MessageApi {

	/**
	 * The message is send towards the client.
	 * 
	 * @param message
	 *            The message to be send to the client.
	 */
	void sendToClient(JsonMessage message);

	/**
	 * Sends the message to all active player bestias in update range. The given
	 * message must refer to an entity source which has a
	 * {@link PositionComponent} attached. Otherwise the message origin position
	 * can not be determined and thus no updates send to players.
	 * 
	 * @param message
	 *            The message to send to all active players inside the update
	 *            range.
	 */
	void sendActiveInRangeClients(EntityJsonMessage message);

	/**
	 * Sends a message to an actor with the given name in the actor system. The
	 * names of actors are usually accessed by a public static string member of
	 * the actor class.
	 * 
	 * @param actorName
	 * @param message
	 */
	void sendToActor(String actorName, Object message);

	/**
	 * Helper to start actors.
	 * 
	 * @param actorClazz
	 * @return
	 */
	ActorRef startActor(Class<? extends AbstractActor> actorClazz);

	/**
	 * Sends a message directly to the entity actor managing a single entity
	 * inside the cluster.
	 * 
	 * @param entityId
	 *            The entity ID which actor should receive the message.
	 * @param msg
	 *            The message to send.
	 */
	void sendEntityActor(long entityId, Object msg);
}
