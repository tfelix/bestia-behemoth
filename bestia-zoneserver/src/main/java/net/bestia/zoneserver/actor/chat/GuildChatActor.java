package net.bestia.zoneserver.actor.chat;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.chat.ChatMessage;

/**
 * Handles guild chats.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class GuildChatActor extends AbstractActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);
	public static final String NAME = "guild";

	@Autowired
	public GuildChatActor() {

	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(ChatMessage.class, this::handleGuild)
				.build();
	}

	/**
	 * Sends a public message to all clients in sight.
	 */
	private void handleGuild(ChatMessage chatMsg) {
		LOG.warning("Chat mode not supported.");
	}

}
