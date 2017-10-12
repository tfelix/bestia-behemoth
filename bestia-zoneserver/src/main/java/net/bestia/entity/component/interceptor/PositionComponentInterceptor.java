package net.bestia.entity.component.interceptor;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import net.bestia.messages.cluster.entity.EntityComponentMessage;
import net.bestia.messages.entity.EntityPositionMessage;
import net.bestia.messages.entity.EntityUpdateMessage;
import net.bestia.model.geometry.Point;
import net.bestia.zoneserver.actor.zone.ZoneAkkaApi;
import net.bestia.entity.Entity;
import net.bestia.entity.EntityService;
import net.bestia.entity.component.PositionComponent;

/**
 * Every active player entity in sight is updated about the entity movement.
 * 
 * @author Thomas Felix
 *
 */
@Component
public class PositionComponentInterceptor extends BaseComponentInterceptor<PositionComponent> {

	private static final Logger LOG = LoggerFactory.getLogger(PositionComponentInterceptor.class);

	private final ZoneAkkaApi akkaApi;

	public PositionComponentInterceptor(ZoneAkkaApi akkaApi) {
		super(PositionComponent.class);

		this.akkaApi = Objects.requireNonNull(akkaApi);
	}

	@Override
	protected void onUpdateAction(EntityService entityService, Entity entity, PositionComponent comp) {
		LOG.trace("Position component is updated.");

		// Update all active players in sight with the new position path.
		final EntityPositionMessage posMessage = new EntityPositionMessage(entity.getId(), comp.getPosition());
		akkaApi.sendActiveInRangeClients(posMessage);
	}

	@Override
	protected void onCreateAction(EntityService entityService, Entity entity, PositionComponent comp) {
		LOG.trace("Position component created.");

		final EntityComponentMessage msg = EntityComponentMessage.start(entity.getId(), comp.getId());
		akkaApi.sendEntityActor(entity.getId(), msg);
	}

	@Override
	protected void onDeleteAction(EntityService entityService, Entity entity, PositionComponent comp) {
		LOG.debug("Recycling position component.");

		final long entityId = comp.getEntityId();

		// Find the position of the entity to be send inside the message.
		// The position is important because the entity is removed right here
		// but we need to send the update messages to the clients, which in turn
		// is dependent on the position.
		// We need therefore cache this data.
		final Point position = comp.getPosition();

		// Send status update to all clients in range.
		final EntityUpdateMessage updateMsg = EntityUpdateMessage.getDespawnUpdate(entityId, position);
		akkaApi.sendActiveInRangeClients(updateMsg);
	}

}
