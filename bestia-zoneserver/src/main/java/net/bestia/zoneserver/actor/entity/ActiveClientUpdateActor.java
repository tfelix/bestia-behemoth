package net.bestia.zoneserver.actor.entity;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.EntityJsonMessage;
import net.bestia.messages.EntityMessage;
import net.bestia.messages.JsonMessage;
import net.bestia.model.geometry.Rect;
import net.bestia.model.map.Map;
import net.bestia.zoneserver.actor.BestiaRoutingActor;
import net.bestia.zoneserver.entity.traits.Locatable;
import net.bestia.zoneserver.service.EntityService;
import net.bestia.zoneserver.service.PlayerEntityService;

/**
 * This actor sends update messages to all active player in sight. In order to
 * perform the the sending of the message the message must inherit both the
 * {@link EntityMessage} interface and also {@link JsonMessage}.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Component
@Scope("prototype")
public class ActiveClientUpdateActor extends BestiaRoutingActor {

	public final static String NAME = "activeClientUpdate";
	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	private final EntityService entityService;
	private final PlayerEntityService playerEntityService;

	public ActiveClientUpdateActor(EntityService entityService, PlayerEntityService playerService) {
		super(Arrays.asList(EntityJsonMessage.class, JsonMessage.class));

		this.entityService = Objects.requireNonNull(entityService);
		this.playerEntityService = Objects.requireNonNull(playerService);
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
			final List<Long> activeAccs = playerEntityService.getActiveAccountIdsInRange(updateRect);

			// Check if the pbe are active and if so send them the update.
			for (long activeAcc : activeAccs) {

				sendClient(dataMsg.createNewInstance(activeAcc));

			}
		} catch (ClassCastException e) {
			LOG.error("Updating entity is not of trait Locatable: {}", e.getMessage());
		}
	}

}
