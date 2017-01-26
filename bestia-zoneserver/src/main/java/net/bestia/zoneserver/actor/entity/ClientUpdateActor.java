package net.bestia.zoneserver.actor.entity;

import java.util.Collection;
import java.util.Objects;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.JsonMessage;
import net.bestia.messages.internal.entity.ActiveUpateMessage;
import net.bestia.model.geometry.Point;
import net.bestia.model.geometry.Rect;
import net.bestia.model.map.Map;
import net.bestia.zoneserver.actor.BestiaActor;
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
public class ClientUpdateActor extends BestiaActor {

	public final static String NAME = "activePlayerUpdate";
	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	private final EntityService entityService;

	public ClientUpdateActor(EntityService entityService) {

		this.entityService = Objects.requireNonNull(entityService);
	}

	protected Rect getUpdateRect(Point pos) {
		return new Rect(pos.getX() - Map.SIGHT_RANGE * 2,
				pos.getY() - Map.SIGHT_RANGE * 2,
				pos.getX() + Map.SIGHT_RANGE * 2,
				pos.getY() + Map.SIGHT_RANGE * 2);
	}

	@Override
	public void onReceive(Object msg) throws Throwable {
		LOG.debug("Received: {}", msg.toString());
		
		// Handle only ActiveUpdateMessage
		if(!(msg instanceof ActiveUpateMessage)) {
			unhandled(msg);
			return;
		}
		
		final ActiveUpateMessage updateMsg = (ActiveUpateMessage) msg;
		final JsonMessage dataMsg = updateMsg.getUpdateMessage();

		// Send message to the owner if its an player entity.
		try {
			final Locatable movingEntity = entityService.getEntity(updateMsg.getEntityId(), Locatable.class);

			// Find all active player bestias in range.
			final Rect updateRect = getUpdateRect(movingEntity.getPosition());
			final Collection<PlayerEntity> pbes = entityService.getEntitiesInRange(updateRect,
					PlayerEntity.class);

			// Check if the pbe are active and if so send them the update.
			for (PlayerEntity pbe : pbes) {
				if (pbe.isActive()) {
					dataMsg.setAccountId(pbe.getAccountId());
					sendClient(dataMsg);
				}
			}

		} catch (ClassCastException e) {
			LOG.error("Updating entity is not of trait Locatable: {}", e.getMessage());
		}
	}

}
