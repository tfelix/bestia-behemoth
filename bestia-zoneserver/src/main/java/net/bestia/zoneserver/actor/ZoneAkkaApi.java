package net.bestia.zoneserver.actor;

import akka.actor.ActorPath;
import akka.actor.ActorRef;
import akka.actor.UntypedActor;
import net.bestia.messages.EntityJsonMessage;
import net.bestia.messages.JsonMessage;

/**
 * This is the interface for a typed actor for internal message routing.
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
	 * Helper to start actors.
	 * 
	 * @param actorClazz
	 * @return
	 */
	ActorRef startActor(Class<? extends UntypedActor> actorClazz);

	ActorRef startUnnamedActor(Class<? extends UntypedActor> actorClazz);

	void sendToActor(ActorPath actorPath, Object message);

}
