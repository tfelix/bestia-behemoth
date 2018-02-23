package net.bestia.zoneserver.actor.chat;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.entity.Entity;
import net.bestia.entity.EntityService;
import net.bestia.entity.component.PositionComponent;
import net.bestia.messages.chat.ChatMessage;
import net.bestia.zoneserver.actor.SpringExtension;
import net.bestia.zoneserver.actor.zone.SendClientsInRangeActor;
import net.bestia.zoneserver.entity.PlayerEntityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

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
	private final ActorRef sendActiveRange;

	@Autowired
	public PublicChatActor(PlayerEntityService playerEntityService,
			EntityService entityService) {
		
		this.playerEntityService = Objects.requireNonNull(playerEntityService);
		this.entityService = Objects.requireNonNull(entityService);
		this.sendActiveRange = SpringExtension.actorOf(getContext(), SendClientsInRangeActor.class);
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
		sendActiveRange.tell(chatEntityMsg, getSelf());
	}

}
