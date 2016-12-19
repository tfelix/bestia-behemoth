package net.bestia.zoneserver.entity;

import java.util.Objects;

import akka.actor.ActorRef;
import net.bestia.messages.chat.ChatMessage;
import net.bestia.messages.entity.EntityPositionMessage;
import net.bestia.zoneserver.actor.entity.EntityContextActor;

/**
 * This class is used to give all entities a callback option in order to submit
 * messages back to the actor system.
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
	 * Should be called if an entities position has been changed since the last
	 * call. It then will update the new position to all entities in sight
	 * range.
	 * 
	 * @param e
	 */
	public void notifyPosition(BaseEntity e) {
		final EntityPositionMessage epmsg = new EntityPositionMessage(e.getId(), e.getX(), e.getY());
		actor.tell(epmsg, ActorRef.noSender());
	}

	/**
	 * Sends a message to the given account (which must be online).
	 * 
	 * @param accId
	 *            Account to receive this message.
	 * @param msg
	 *            The message send to the account.
	 */
	public void sendMessage(long accId, String msg, ChatMessage.Mode mode) {
		final ChatMessage chatMsg = new ChatMessage(accId, msg, mode);
		actor.tell(chatMsg, ActorRef.noSender());
	}
}
