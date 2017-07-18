package net.bestia.entity.recycler;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.bestia.entity.EntityService;
import net.bestia.entity.component.StatusComponent;
import net.bestia.messages.internal.entity.EntityRegenTickMessage;
import net.bestia.zoneserver.actor.zone.ZoneAkkaApi;

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
		
		final EntityRegenTickMessage msg = new EntityRegenTickMessage(component.getEntityId(), false);
		akkaApi.sendEntityActor(component.getEntityId(), msg);
	}

}
