package net.bestia.zoneserver.actor.entity;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.entity.EntityPositionMessage;
import net.bestia.model.geometry.Point;
import net.bestia.model.geometry.Rect;
import net.bestia.model.map.Map;
import net.bestia.zoneserver.actor.BestiaRoutingActor;
import net.bestia.zoneserver.entity.PlayerBestiaEntity;
import net.bestia.zoneserver.entity.traits.Moving;
import net.bestia.zoneserver.service.EntityService;

/**
 * This actor sends update messages regarding to entities to all active players
 * in range of the emitting entity.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Component
@Scope("prototype")
public class EntityUpdatePositionActor extends BestiaRoutingActor {

	public final static String NAME = "entityPositionUpdate";
	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	private final EntityService entityService;

	public EntityUpdatePositionActor(EntityService entityService) {
		super(Arrays.asList(EntityPositionMessage.class));

		this.entityService = Objects.requireNonNull(entityService);
	}

	protected Rect getUpdateRect(Point pos) {
		return new Rect(pos.getX() - Map.SIGHT_RANGE * 2,
				pos.getY() - Map.SIGHT_RANGE * 2,
				pos.getX() + Map.SIGHT_RANGE * 2,
				pos.getY() + Map.SIGHT_RANGE * 2);
	}

	@Override
	protected void handleMessage(Object msg) {
		LOG.debug("Received: {}", msg.toString());

		final EntityPositionMessage posMsg = (EntityPositionMessage) msg;

		// Send message to the owner if its an player entity.
		try {
			final Moving movingEntity = entityService.getEntity(posMsg.getEntityId(), Moving.class);

			if (movingEntity instanceof PlayerBestiaEntity) {
				// Send update to the player.
				posMsg.setAccountId(((PlayerBestiaEntity) movingEntity).getAccountId());
				sendClient(posMsg);
			}

			// Find all active player bestias in range.
			final Rect updateRect = getUpdateRect(movingEntity.getPosition());
			final Collection<PlayerBestiaEntity> pbes = entityService.getEntitiesInRange(updateRect,
					PlayerBestiaEntity.class);

			// Check if the pbe are active and if so send them the update.
			for (PlayerBestiaEntity pbe : pbes) {
				if (pbe.isActive()) {
					posMsg.setAccountId(pbe.getAccountId());
					sendClient(posMsg);
				}
			}

		} catch (ClassCastException e) {
			LOG.error("Moved entity is not of trait Locatable: {}", e.getMessage());
		}
	}

}
