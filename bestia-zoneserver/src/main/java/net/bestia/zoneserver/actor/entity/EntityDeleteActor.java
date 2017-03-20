package net.bestia.zoneserver.actor.entity;

import java.util.Arrays;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import net.bestia.messages.entity.EntityDeleteInternalMessage;
import net.bestia.messages.entity.EntityUpdateMessage;
import net.bestia.model.geometry.Point;
import net.bestia.zoneserver.actor.BestiaRoutingActor;
import net.bestia.zoneserver.entity.traits.Entity;
import net.bestia.zoneserver.entity.traits.Locatable;
import net.bestia.zoneserver.service.EntityService;

/**
 * This actor will handle the removing of an entity from the system.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class EntityDeleteActor extends BestiaRoutingActor {

	public static final String NAME = "entityDespawn";

	private final EntityService entityService;

	/**
	 * 
	 */
	@Autowired
	public EntityDeleteActor(EntityService entityService) {
		super(Arrays.asList(EntityDeleteInternalMessage.class));

		this.entityService = Objects.requireNonNull(entityService);
	}

	@Override
	protected void handleMessage(Object msg) {

		final EntityDeleteInternalMessage delMsg = (EntityDeleteInternalMessage) msg;

		final Entity e = entityService.getEntity(delMsg.getEntityId());

		// Find the position of the entity to be send inside the message.
		// The position is important because the entity is removed right here
		// but we need to send the update messages to the clients, which in turn
		// is dependent on the position.
		// We need therefore cache this data.
		final Point position;
		if (e != null && e instanceof Locatable) {
			position = ((Locatable) e).getPosition();
		} else {
			position = new Point(0, 0);
		}

		entityService.delete(delMsg.getEntityId());

		// Send status update to all clients in range.
		sendActiveInRangeClients(EntityUpdateMessage.getDespawnUpdate(delMsg.getEntityId(), position));

	}

}
