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
import net.bestia.zoneserver.entity.Entity;
import net.bestia.zoneserver.entity.EntityServiceContext;

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

	private final EntityServiceContext srvCtx;

	@Autowired
	public EntityMoveActor(EntityServiceContext srvCtx) {
		super(Arrays.asList(EntityMoveMessage.class));

		this.srvCtx = Objects.requireNonNull(srvCtx);
	}

	@Override
	protected void handleMessage(Object msg) {
		LOG.debug("Received player move message: {}", msg.toString());
		
		EntityMoveMessage moveMsg = (EntityMoveMessage) msg;
		
		final Entity playerEntity = srvCtx.getPlayer().getActivePlayerEntity(moveMsg.getAccountId());

		if (playerEntity.getId() != moveMsg.getEntityId()) {
			LOG.warning("Player {} does not own entity {}.", moveMsg.getAccountId(), moveMsg.getEntityId());
			return;
		}

		srvCtx.getMove().moveTo(playerEntity, moveMsg.getPath());
	}

	
}
