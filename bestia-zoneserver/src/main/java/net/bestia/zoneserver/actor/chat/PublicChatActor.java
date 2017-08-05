package net.bestia.zoneserver.actor.chat;

import java.util.Objects;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.entity.Entity;
import net.bestia.entity.EntityService;
import net.bestia.entity.PlayerEntityService;
import net.bestia.entity.component.PositionComponent;
import net.bestia.messages.chat.ChatMessage;
import net.bestia.zoneserver.AkkaSender;

/**
 * Handles public chat of the user and sends them to all entities which can
 * receive them.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class PublicChatActor extends AbstractActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);
	public static final String NAME = "public";

	private final PlayerEntityService playerEntityService;
	private final EntityService entityService;

	@Autowired
	public PublicChatActor(PlayerEntityService playerEntityService,
			EntityService entityService) {
		
		this.playerEntityService = Objects.requireNonNull(playerEntityService);
		this.entityService = Objects.requireNonNull(entityService);
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(ChatMessage.class, this::handlePublic)
				.build();
	}

	/**
	 * Sends a public message to all clients in sight.
	 */
	private void handlePublic(ChatMessage chatMsg) {
		// Sanity check.
		if(chatMsg.getChatMode() != ChatMessage.Mode.PUBLIC) {
			LOG.warning("Can not handle non public chat messages: {}.", chatMsg);
			unhandled(chatMsg);
			return;
		}
		
		final long accId = chatMsg.getAccountId();
		final Entity pbe = playerEntityService.getActivePlayerEntity(accId);

		if (pbe == null) {
			return;
		}

		// Add the current entity id to the message.
		final ChatMessage chatEntityMsg = new ChatMessage(accId, pbe.getId(), chatMsg);

		Optional<PositionComponent> pos = entityService.getComponent(pbe, PositionComponent.class);

		if (!pos.isPresent()) {
			LOG.warning("Player bestia has no position component.");
			return;
		}

		// We dont need to send a echo back because the player entity is also
		// active in the area so this call also includes the sender of the chat
		// message.
		AkkaSender.sendActiveInRangeClients(getContext(), chatEntityMsg);
	}

}
