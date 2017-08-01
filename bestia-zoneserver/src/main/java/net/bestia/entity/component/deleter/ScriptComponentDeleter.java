package net.bestia.entity.component.deleter;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.bestia.entity.EntityService;
import net.bestia.entity.component.ScriptComponent;
import net.bestia.messages.internal.entity.EntityComponentMessage;
import net.bestia.zoneserver.actor.zone.ZoneAkkaApi;

/**
 * If a {@link ScriptComponent} is deleted the associated actor will get
 * removed.
 * 
 * @author Thomas Felix
 *
 */
@Component
public class ScriptComponentDeleter extends ComponentDeleter<ScriptComponent> {

	private static final Logger LOG = LoggerFactory.getLogger(ScriptComponentDeleter.class);

	private final ZoneAkkaApi akkaApi;

	@Autowired
	protected ScriptComponentDeleter(EntityService entityService, ZoneAkkaApi akkaApi) {
		super(entityService, ScriptComponent.class);

		this.akkaApi = Objects.requireNonNull(akkaApi);
	}

	@Override
	protected void doFreeComponent(ScriptComponent component) {
		// Stop the actor timing the entity component.
		LOG.trace("ScriptComponent deleted. Stopping actor.");

		EntityComponentMessage msg = EntityComponentMessage.stop(component.getEntityId(), component.getId());
		akkaApi.sendEntityActor(component.getEntityId(), msg);
	}

}
