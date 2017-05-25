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
import net.bestia.zoneserver.entity.Entity;
import net.bestia.zoneserver.entity.EntityService;
import net.bestia.zoneserver.entity.component.PositionComponent;

/**
 * This actor will handle the removing of an entity from the system. It will
 * notify all clients about the entity which was deleted from the system.
 * 
 * FIXME Wir machen das hier anders: Löschungen gehen von Services aus und
 * werden dann als MSG in AKKA transportiert.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class EntityDeleteActor extends BestiaRoutingActor {

	public static final String NAME = "entityDelete";

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
		final Point position = entityService.getComponent(e, PositionComponent.class)
				.map(PositionComponent::getPosition)
				.orElse(new Point(0, 0));

		entityService.delete(delMsg.getEntityId());

		// Send status update to all clients in range.
		sendActiveInRangeClients(EntityUpdateMessage.getDespawnUpdate(delMsg.getEntityId(), position));

	}

}
