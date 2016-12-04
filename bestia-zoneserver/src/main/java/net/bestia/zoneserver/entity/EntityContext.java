package net.bestia.zoneserver.entity;

import java.util.Objects;

import org.springframework.stereotype.Component;

import akka.actor.ActorRef;
import net.bestia.messages.chat.ChatMessage;

/**
 * This class is used to give all entities a callback option in order to submit
 * messages back to the actor system.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Component
public class EntityContext {
	
	private final ActorRef actor;
	
	public EntityContext(ActorRef actor) {
		
		this.actor = Objects.requireNonNull(actor);
	}

	/**
	 * Should be
	 * 
	 * @param e
	 */
	public void notifyChanged(BaseEntity e) {

	}

	public void notifyPosition(BaseEntity e) {

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
