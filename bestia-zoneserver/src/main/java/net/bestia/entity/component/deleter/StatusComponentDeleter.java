package net.bestia.entity.component.deleter;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.bestia.entity.EntityService;
import net.bestia.entity.component.StatusComponent;
import net.bestia.messages.internal.entity.EntityComponentMessage;
import net.bestia.zoneserver.actor.zone.ZoneAkkaApi;

/**
 * Removes the entity actor responsible for ticking the status updates.
 * 
 * @author Thomas Felix
 *
 */
@Component
public class StatusComponentDeleter extends ComponentDeleter<StatusComponent> {

	private static final Logger LOG = LoggerFactory.getLogger(StatusComponentDeleter.class);

	private final ZoneAkkaApi akkaApi;

	@Autowired
	protected StatusComponentDeleter(EntityService entityService, ZoneAkkaApi akkaApi) {
		super(entityService, StatusComponent.class);

		this.akkaApi = Objects.requireNonNull(akkaApi);
	}

	@Override
	protected void doFreeComponent(StatusComponent component) {
		// Stop the actor timing the entity component.
		LOG.trace("StatusComponent deleted. Stopping updates.");

		final EntityComponentMessage msg = EntityComponentMessage.stop(component.getEntityId(), component.getId());
		akkaApi.sendEntityActor(component.getEntityId(), msg);
	}

}
