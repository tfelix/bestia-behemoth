package net.bestia.entity.component.deleter;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.bestia.entity.EntityService;
import net.bestia.entity.component.PositionComponent;
import net.bestia.messages.entity.EntityUpdateMessage;
import net.bestia.model.geometry.Point;
import net.bestia.zoneserver.actor.zone.ZoneAkkaApi;

@Component
public class PositionComponentDeleter extends ComponentDeleter<PositionComponent> {
	
	private static final Logger LOG = LoggerFactory.getLogger(PositionComponentDeleter.class);
	
	private final ZoneAkkaApi akkaApi;

	@Autowired
	public PositionComponentDeleter(EntityService entityService, ZoneAkkaApi akkaApi) {
		super(entityService, PositionComponent.class);
		
		this.akkaApi = Objects.requireNonNull(akkaApi);
	}

	@Override
	protected void doFreeComponent(PositionComponent component) {
		
		LOG.debug("Recycling position component.");
		
		final long entityId = component.getEntityId();

		// Find the position of the entity to be send inside the message.
		// The position is important because the entity is removed right here
		// but we need to send the update messages to the clients, which in turn
		// is dependent on the position.
		// We need therefore cache this data.
		final Point position = component.getPosition();

		// Send status update to all clients in range.
		final EntityUpdateMessage updateMsg = EntityUpdateMessage.getDespawnUpdate(entityId, position);
		akkaApi.sendActiveInRangeClients(updateMsg);
	}

}
