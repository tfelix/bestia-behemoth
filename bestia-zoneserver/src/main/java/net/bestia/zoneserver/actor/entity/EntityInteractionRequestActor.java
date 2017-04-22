package net.bestia.zoneserver.actor.entity;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.entity.EntityInteractionMessage;
import net.bestia.messages.entity.EntityInteractionRequestMessage;
import net.bestia.model.entity.InteractionType;
import net.bestia.zoneserver.actor.BestiaRoutingActor;
import net.bestia.zoneserver.entity.Entity;
import net.bestia.zoneserver.entity.EntityService;
import net.bestia.zoneserver.entity.InteractionService;
import net.bestia.zoneserver.entity.PlayerEntityService;
import net.bestia.zoneserver.entity.components.InteractionComponent;

/**
 * Receives interaction requests for an entity. It will query the system and ask
 * the entity how the player is able to interact with it.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Component
@Scope("prototype")
public class EntityInteractionRequestActor extends BestiaRoutingActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);
	public final static String NAME = "requestInteract";

	private final EntityService entityService;
	private final InteractionService interactService;
	private final PlayerEntityService playerEntityService;

	@Autowired
	public EntityInteractionRequestActor(EntityService entityService, PlayerEntityService pes,
			InteractionService interactService) {
		super(Arrays.asList(EntityInteractionRequestMessage.class));

		// FIXME Entitiy basierte services in ein context object auslagern.
		this.entityService = Objects.requireNonNull(entityService);
		this.playerEntityService = Objects.requireNonNull(pes);
		this.interactService = Objects.requireNonNull(interactService);
	}

	@Override
	protected void handleMessage(Object msg) {

		final EntityInteractionRequestMessage rm = (EntityInteractionRequestMessage) msg;
		final Entity entity = entityService.getEntity(rm.getEntityId());

		if (entity == null) {
			LOG.warning("Entity not found. Message was: {}", msg.toString());
			return;
		}

		final Optional<InteractionComponent> interactionComp = entityService.getComponent(entity,
				InteractionComponent.class);

		// Entity does not seam to interact.
		if (!interactionComp.isPresent()) {
			sendClient(new EntityInteractionMessage(rm.getAccountId(), rm.getEntityId(), InteractionType.NONE));
			return;
		} else {
			final Entity pbe = playerEntityService.getActivePlayerEntity(rm.getEntityId());
			final Set<InteractionType> interactions = interactService.getPossibleInteractions(pbe, entity);
			sendClient(new EntityInteractionMessage(rm.getAccountId(), rm.getEntityId(), interactions));
		}
	}

}
