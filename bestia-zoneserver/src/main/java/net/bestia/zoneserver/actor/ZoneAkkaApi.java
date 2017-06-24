package net.bestia.zoneserver.actor;

import akka.actor.AbstractActor;
import akka.actor.ActorPath;
import akka.actor.ActorRef;
import net.bestia.messages.EntityJsonMessage;
import net.bestia.messages.JsonMessage;

/**
 * This is the zentral interface for any external component like services or
 * components to interact with the akka system.
 * 
 * @author Thomas Felix
 *
 */
public interface ZoneAkkaApi {

	/**
	 * The message is send towards the client.
	 * 
	 * @param message
	 *            The message to be send to the client.
	 */
	void sendToClient(JsonMessage message);

	void sendActiveInRangeClients(EntityJsonMessage message);

	/**
	 * Sends a message to an actor in the actor system.
	 * 
	 * @param actorName
	 * @param message
	 */
	void sendToActor(String actorName, Object message);

	/**
	 * Sends a message to an actors just as
	 * {@link #sendToActor(String, Object)}. In this case only a
	 * {@link ActorPath} is used instead of a simple name.
	 * 
	 * @param actorPath
	 * @param message
	 */
	void sendToActor(ActorPath actorPath, Object message);

	/**
	 * Helper to start actors.
	 * 
	 * @param actorClazz
	 * @return
	 */
	ActorRef startActor(Class<? extends AbstractActor> actorClazz);

	/**
	 * This starts an unnamed actor. Which is faster then starting a named
	 * actor. If many one-shot actors should be fired off then this method is
	 * preferred to {@link #startActor(Class)}.
	 * 
	 * @param actorClazz
	 * @return
	 */
	ActorRef startUnnamedActor(Class<? extends AbstractActor> actorClazz);

	/**
	 * Sends a message directly to the entity actor managing a single entity
	 * inside the cluster.
	 * 
	 * @param id The entity ID which actor should receive the message.
	 * @param msg The message to send.
	 */
	void sendEntityActor(long id, Object msg);
}
