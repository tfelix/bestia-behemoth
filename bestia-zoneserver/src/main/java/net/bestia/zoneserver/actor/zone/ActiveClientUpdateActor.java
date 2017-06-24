package net.bestia.zoneserver.actor.zone;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.EntityMessage;
import net.bestia.messages.JsonMessage;
import net.bestia.messages.entity.EntityUpdateMessage;
import net.bestia.model.geometry.Point;
import net.bestia.model.geometry.Rect;
import net.bestia.zoneserver.AkkaSender;
import net.bestia.zoneserver.actor.BestiaActor;
import net.bestia.entity.Entity;
import net.bestia.entity.EntityService;
import net.bestia.entity.component.PositionComponent;
import net.bestia.zoneserver.map.MapService;
import net.bestia.zoneserver.service.PlayerEntityService;

/**
 * This actor sends update messages to all active player in sight. In order to
 * perform the the sending of the message the message must inherit both the
 * {@link EntityMessage} interface and also {@link JsonMessage}.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class ActiveClientUpdateActor extends BestiaActor {

	public final static String NAME = "activeClientUpdate";
	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	private final EntityService entityService;
	private final PlayerEntityService playerEntityService;

	@Autowired
	public ActiveClientUpdateActor(EntityService entityService, PlayerEntityService playerService) {

		this.entityService = Objects.requireNonNull(entityService);
		this.playerEntityService = Objects.requireNonNull(playerService);
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.matchAny(this::handleMessage)
				.build();
	}

	public void handleMessage(Object msg) {
		LOG.debug("Received: {}", msg.toString());

		// Handle only ActiveUpdateMessage
		if (!(msg instanceof EntityMessage) || !(msg instanceof JsonMessage)) {
			LOG.warning("Can not send message to client. Message does not inherit from EntityMessage AND JsonMessage.");
			unhandled(msg);
			return;
		}

		final EntityMessage entityMsg = (EntityMessage) msg;
		final JsonMessage dataMsg = (JsonMessage) msg;

		// Send message to the owner if its an player entity.
		try {
			final Entity entity = entityService.getEntity(entityMsg.getEntityId());

			// If the entity could not be found the rely on the position
			// information in the message itself.
			final Point entityPos;
			if (entity == null) {
				if (entityMsg instanceof EntityUpdateMessage) {
					final EntityUpdateMessage umsg = (EntityUpdateMessage) msg;
					entityPos = new Point(umsg.getX(), umsg.getY());
				} else {
					// We have no position information and cant update any
					// client.
					LOG.warning("Could not regenerate position info for entity {}. Message was: {}.",
							entityMsg.getEntityId(), msg);
					return;
				}
			} else {
				final Optional<PositionComponent> posComp = entityService.getComponent(entity, PositionComponent.class);

				if (!posComp.isPresent()) {
					// We have no position information and cant update any
					// client.
					LOG.warning("Could not regenerate position info for entity {}. Message was: {}.",
							entityMsg.getEntityId(), msg);
					return;
				} else {
					entityPos = posComp.get().getPosition();
				}
			}

			// Find all active player accounts in visible/update range.
			final Rect updateRect = MapService.getUpdateRect(entityPos);
			final List<Long> activeAccs = playerEntityService.getActiveAccountIdsInRange(updateRect);

			// Check if the pbe are active and if so send them the update.
			for (long activeAcc : activeAccs) {

				AkkaSender.sendClient(getContext(), dataMsg.createNewInstance(activeAcc));

			}
		} catch (ClassCastException e) {
			LOG.error("Updating entity has no Position component: {}", e.getMessage());
		}
	}

}
