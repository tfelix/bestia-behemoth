package net.bestia.entity.component.interceptor;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import net.bestia.messages.entity.EntityPositionMessage;
import net.bestia.messages.internal.entity.EntityComponentMessage;
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
public class PositionComponentInterceptor extends ComponentInterceptor<PositionComponent> {

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

}
