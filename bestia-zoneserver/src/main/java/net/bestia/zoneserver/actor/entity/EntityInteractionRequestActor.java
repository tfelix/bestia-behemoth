package net.bestia.zoneserver.actor.entity;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.entity.Entity;
import net.bestia.entity.EntityService;
import net.bestia.entity.InteractionService;
import net.bestia.entity.PlayerEntityService;
import net.bestia.entity.component.InteractionComponent;
import net.bestia.messages.entity.EntityInteractionMessage;
import net.bestia.messages.entity.EntityInteractionRequestMessage;
import net.bestia.model.entity.InteractionType;
import net.bestia.zoneserver.AkkaSender;
import net.bestia.zoneserver.actor.zone.IngestExActor.RedirectMessage;

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

	private final EntityService entityService;
	private final InteractionService interactService;
	private final PlayerEntityService playerEntityService;

	@Autowired
	public EntityInteractionRequestActor(EntityService entityService,
			PlayerEntityService pes,
			InteractionService interactService) {

		// FIXME Entitiy basierte services in ein context object auslagern.
		this.entityService = Objects.requireNonNull(entityService);
		this.playerEntityService = Objects.requireNonNull(pes);
		this.interactService = Objects.requireNonNull(interactService);
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

		final Entity entity = entityService.getEntity(msg.getEntityId());

		if (entity == null) {
			LOG.warning("Entity not found. Message was: {}", msg.toString());
			return;
		}

		final Optional<InteractionComponent> interactionComp = entityService.getComponent(entity,
				InteractionComponent.class);

		// Entity does not seam to interact.
		if (!interactionComp.isPresent()) {
			final EntityInteractionMessage reply = new EntityInteractionMessage(
					msg.getAccountId(),
					msg.getEntityId(),
					InteractionType.NONE);
			AkkaSender.sendClient(getContext(), reply);

			return;
		} else {
			final Entity pbe = playerEntityService.getActivePlayerEntity(msg.getEntityId());
			final Set<InteractionType> interactions = interactService.getPossibleInteractions(pbe, entity);
			final EntityInteractionMessage reply = new EntityInteractionMessage(
					msg.getAccountId(),
					msg.getEntityId(),
					interactions);
			AkkaSender.sendClient(getContext(), reply);
		}
	}

}
