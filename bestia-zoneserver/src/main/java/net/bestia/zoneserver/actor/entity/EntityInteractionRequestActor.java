package net.bestia.zoneserver.actor.entity;

import java.util.Arrays;
import java.util.Objects;
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
import net.bestia.zoneserver.entity.PlayerEntity;
import net.bestia.zoneserver.entity.PlayerEntityService;
import net.bestia.zoneserver.entity.traits.Interactable;

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
	private final PlayerEntityService playerEntityService;
	
	@Autowired
	public EntityInteractionRequestActor(EntityService entityService, PlayerEntityService pes) {
		super(Arrays.asList(EntityInteractionRequestMessage.class));

		this.entityService = Objects.requireNonNull(entityService);
		this.playerEntityService = Objects.requireNonNull(pes);
	}

	@Override
	protected void handleMessage(Object msg) {
		
		final EntityInteractionRequestMessage rm = (EntityInteractionRequestMessage) msg;	
		final Entity entity = entityService.getEntity(rm.getEntityId());
		
		if(entity == null) {
			LOG.warning("Entity not found. Message was: {}", msg.toString());
			return;
		}
		
		Interactable interactableComp = entity.getComponent(Interactable.class);
		
		// Is it a interactable entity?
		if(!(entity instanceof Interactable)) {
			sendClient(new EntityInteractionMessage(rm.getAccountId(), rm.getEntityId(), InteractionType.NONE));
			return;
		}
		
		final PlayerEntity pbe = playerEntityService.getActivePlayerEntity(rm.getEntityId());
		final Set<InteractionType> interactions = ((Interactable) entity).getPossibleInteractions(pbe);
		sendClient(new EntityInteractionMessage(rm.getAccountId(), rm.getEntityId(), interactions));
	}

}
