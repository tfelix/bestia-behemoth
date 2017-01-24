package net.bestia.zoneserver.entity;

import java.util.Objects;

import akka.actor.ActorRef;
import net.bestia.messages.Message;
import net.bestia.zoneserver.actor.entity.EntityContextActor;

/**
 * This class is used to give all entities a callback option in order to submit
 * messages back to the actor system and/or the connected clients.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class EntityContext {

	private final ActorRef actor;

	/**
	 * Ctor.
	 * 
	 * @param actor
	 *            Entry point for handling the entity messaging. This is usually
	 *            done via an {@link EntityContextActor}.
	 */
	public EntityContext(ActorRef actor) {

		this.actor = Objects.requireNonNull(actor);
	}

	/**
	 * Sends a message to the client or clients. The messaging framework will
	 * check to find out to whom the message must be delivered.
	 * 
	 * @param msg
	 *            The message which shall be send to the client(s).
	 */
	public void sendMessage(Message msg) {
		actor.tell(msg, ActorRef.noSender());
	}
}
