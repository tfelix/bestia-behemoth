package net.bestia.zoneserver.actor.entity;

import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.entity.EntityService;
import net.bestia.messages.entity.EntityInteractionMessage;
import net.bestia.messages.entity.EntityInteractionRequestMessage;
import net.bestia.model.entity.InteractionType;
import net.bestia.zoneserver.actor.SpringExtension;
import net.bestia.zoneserver.actor.zone.SendClientActor;
import net.bestia.zoneserver.actor.zone.ClientMessageActor.RedirectMessage;
import net.bestia.zoneserver.entity.InteractionService;

/**
 * Receives interaction requests for an entity. It will query the system and ask
 * the entity how the player is able to interact with it.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class EntityInteractionRequestActor extends AbstractActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);
	public final static String NAME = "requestInteract";

	private final InteractionService interactService;
	private final ActorRef sendClient;

	@Autowired
	public EntityInteractionRequestActor(EntityService entityService,
			InteractionService interactService) {
	
		this.interactService = Objects.requireNonNull(interactService);
		sendClient = SpringExtension.actorOf(getContext(), SendClientActor.class);
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(EntityInteractionRequestMessage.class, this::onInteractionRequest)
				.build();
	}

	@Override
	public void preStart() throws Exception {
		final RedirectMessage msg = RedirectMessage.get(EntityInteractionRequestMessage.class);
		context().parent().tell(msg, getSelf());
	}

	private void onInteractionRequest(EntityInteractionRequestMessage msg) {
		LOG.debug("Received message: {}", msg);

		// TODO Ist das hier manipulationssicher oder sendet uns der user beliebige entity ids?
		final Set<InteractionType> interactions = interactService.getPossibleInteractions(msg.getEntityId(),
				msg.getInteractedEntityId());
		
		final EntityInteractionMessage reply = new EntityInteractionMessage(
				msg.getAccountId(),
				msg.getEntityId(),
				interactions);
		
		
		sendClient.tell(reply, getSelf());
	}

}
