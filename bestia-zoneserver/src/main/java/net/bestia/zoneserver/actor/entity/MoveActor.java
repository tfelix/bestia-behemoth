package net.bestia.zoneserver.actor.entity;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import net.bestia.messages.Message;
import net.bestia.messages.entity.EntityMoveMessage;
import net.bestia.zoneserver.actor.BestiaRoutingActor;
import net.bestia.zoneserver.zone.entity.VisibleEntity;

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

	private final Set<Class<? extends Message>> HANDLED_CLASSES = Collections.unmodifiableSet(new HashSet<>(
			Arrays.asList(EntityMoveMessage.class)));

	@Override
	protected Set<Class<? extends Message>> getHandledMessages() {
		return HANDLED_CLASSES;
	}

	@Override
	protected void handleMessage(Object msg) {

		final EntityMoveMessage moveMsg = (EntityMoveMessage) msg;

		final VisibleEntity entity = null;

		// TODO Was it a visible entity? If yes update all nearby entities.

		// TODO Was

	}
}
