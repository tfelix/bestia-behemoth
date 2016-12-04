package net.bestia.zoneserver.actor.entity;

import java.util.Arrays;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import net.bestia.messages.entity.EntityMoveMessage;
import net.bestia.zoneserver.actor.BestiaRoutingActor;
import net.bestia.zoneserver.entity.LivingEntity;

/**
 * Upon receiving of a move message we will lookup the movable entity and sets
 * them to the new position.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Component
@Scope("prototype")
public class MoveActor extends BestiaRoutingActor {

	public final static String NAME = "bestiaMove";

	public MoveActor() {
		super(Arrays.asList(EntityMoveMessage.class));
	}

	@Override
	protected void handleMessage(Object msg) {

		final EntityMoveMessage moveMsg = (EntityMoveMessage) msg;

		final LivingEntity entity = null;

		// Check if the entity is already moving.
		// If this is the case cancel the current movement.

		// Then start a new movement via spawning a new movement tick actor with
		// the route to move and the movement speed determines the ticking
		// speed.

		// If it is a visible entity then update all nearby entities with the
		// movement message.
	}
}
