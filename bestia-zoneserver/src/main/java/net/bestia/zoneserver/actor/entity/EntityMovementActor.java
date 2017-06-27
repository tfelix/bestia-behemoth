package net.bestia.zoneserver.actor.entity;

import java.util.Arrays;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.entity.MovingEntityService;
import net.bestia.entity.PlayerEntityService;
import net.bestia.messages.entity.EntityMoveMessage;
import net.bestia.messages.entity.EntityPositionMessage;
import net.bestia.messages.internal.entity.EntityMoveInternalMessage;
import net.bestia.zoneserver.actor.BestiaRoutingActor;

/**
 * Handle movement of an entity. It will announce the intended move path with
 * timing to all clients in sight so they can start to show the walk animation
 * and will perform the movement timer triggers so the unit does move from tile
 * to tile.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class EntityMovementActor extends BestiaRoutingActor {

	public final static String NAME = "movement";

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	private final MovingEntityService movingService;
	private final PlayerEntityService playerEntityService;

	@Autowired
	public EntityMovementActor(MovingEntityService movingService, PlayerEntityService playerEntityService) {
		super(Arrays.asList(EntityMoveInternalMessage.class, EntityMoveMessage.class, EntityPositionMessage.class));

		this.movingService = Objects.requireNonNull(movingService);
		this.playerEntityService = Objects.requireNonNull(playerEntityService);
	}

	@Override
	protected void handleMessage(Object msg) {
		LOG.debug("Received message: {}", msg.toString());

		if (msg instanceof EntityMoveInternalMessage) {
			
			handleMoveInternal((EntityMoveInternalMessage) msg);
			
		} else if (msg instanceof EntityMoveMessage) {
			
			handleMove((EntityMoveMessage) msg);
			
		} else if (msg instanceof EntityPositionMessage) {
			
			handlePosition((EntityPositionMessage) msg);
			
		} else {
			unhandled(msg);
		}
	}

	/**
	 * Sets entity to a fixed position without movement.
	 */
	private void handlePosition(EntityPositionMessage msg) {
		// This message should be coming only from the internal system so we
		// need no security checks.
		movingService.moveToPosition(msg.getEntityId(), msg.getPosition());
	}

	private void handleMove(EntityMoveMessage msg) {
		// We need to check if the user owns this entity.
		if(!playerEntityService.hasPlayerEntity(msg.getAccountId(), msg.getEntityId())) {
			// Player does not own this bestia so we abort.
			return;
		}
		
		movingService.movePath(msg.getEntityId(), msg.getPath());
	}

	private void handleMoveInternal(EntityMoveInternalMessage msg) {
		// This is an internal message. No security checks invoked.
		movingService.movePath(msg.getEntityId(), msg.getPath());
	}
}
