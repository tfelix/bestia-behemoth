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

		// TODO Was it a visible entity? If yes update all nearby entities.

		// TODO Was

	}
}
