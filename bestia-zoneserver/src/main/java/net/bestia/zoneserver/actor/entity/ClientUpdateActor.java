package net.bestia.zoneserver.actor.entity;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.EntityMessage;
import net.bestia.messages.JsonMessage;
import net.bestia.messages.entity.AnimationPlayMessage;
import net.bestia.messages.entity.EntityDamageMessage;
import net.bestia.model.geometry.Rect;
import net.bestia.model.map.Map;
import net.bestia.zoneserver.actor.BestiaRoutingActor;
import net.bestia.zoneserver.entity.PlayerEntity;
import net.bestia.zoneserver.entity.traits.Locatable;
import net.bestia.zoneserver.service.EntityService;

/**
 * This actor sends update messages to all active player in side.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Component
@Scope("prototype")
public class ClientUpdateActor extends BestiaRoutingActor {

	public final static String NAME = "activePlayerUpdate";
	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	private final EntityService entityService;

	public ClientUpdateActor(EntityService entityService) {
		super(Arrays.asList(EntityDamageMessage.class, AnimationPlayMessage.class));

		this.entityService = Objects.requireNonNull(entityService);
	}


	@Override
	protected void handleMessage(Object msg) {
		LOG.debug("Received: {}", msg.toString());

		// Handle only ActiveUpdateMessage
		if (!(msg instanceof EntityMessage) || !(msg instanceof JsonMessage)) {
			LOG.warning("Can not send to client. Message does not inherit from EntityMessage AND JsonMessage.");
			unhandled(msg);
			return;
		}

		final EntityMessage entityMsg = (EntityMessage) msg;
		final JsonMessage dataMsg = (JsonMessage) msg;

		// Send message to the owner if its an player entity.
		try {
			final Locatable movingEntity = entityService.getEntity(entityMsg.getEntityId(), Locatable.class);

			// Find all active player bestias in range.
			final Rect updateRect = Map.getUpdateRect(movingEntity.getPosition());
			final Collection<PlayerEntity> pbes = entityService.getEntitiesInRange(updateRect,
					PlayerEntity.class);

			// Check if the pbe are active and if so send them the update.
			for (PlayerEntity pbe : pbes) {
				/*if (pbe.isActive()) {
					dataMsg.setAccountId(pbe.getAccountId());
					sendClient(dataMsg);
				}*/
				
				// TODO warum ist die PB nicht aktiv?
				dataMsg.setAccountId(pbe.getAccountId());
				sendClient(dataMsg);
			}

		} catch (ClassCastException e) {
			LOG.error("Updating entity is not of trait Locatable: {}", e.getMessage());
		}
	}

}
