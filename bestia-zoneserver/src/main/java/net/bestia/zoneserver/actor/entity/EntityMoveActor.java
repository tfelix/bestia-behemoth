package net.bestia.zoneserver.actor.entity;

import java.util.Arrays;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.entity.EntityMoveMessage;
import net.bestia.zoneserver.actor.BestiaRoutingActor;
import net.bestia.zoneserver.entity.PlayerEntity;
import net.bestia.zoneserver.entity.PlayerEntityService;

/**
 * Upon receiving of a move message we will lookup the movable entity and sets
 * them to the new position.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Component
@Scope("prototype")
public class EntityMoveActor extends BestiaRoutingActor {

	public final static String NAME = "entityMove";

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	private final PlayerEntityService playerEntityService;

	@Autowired
	public EntityMoveActor(PlayerEntityService playerEntityService) {
		super(Arrays.asList(EntityMoveMessage.class));

		this.playerEntityService = Objects.requireNonNull(playerEntityService);
	}

	@Override
	protected void handleMessage(Object msg) {
		LOG.debug("Received player move message: {}", msg.toString());
		
		EntityMoveMessage moveMsg = (EntityMoveMessage) msg;
		
		final PlayerEntity playerEntity = playerEntityService.getActivePlayerEntity(moveMsg.getAccountId());

		if (playerEntity.getId() != moveMsg.getEntityId()) {
			LOG.warning("Player {} does not own entity {}.", moveMsg.getAccountId(), moveMsg.getEntityId());
			return;
		}

		playerEntity.moveTo(moveMsg.getPath());
	}

	
}
