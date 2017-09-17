package net.bestia.entity.component.interceptor;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import net.bestia.entity.Entity;
import net.bestia.entity.EntityService;
import net.bestia.entity.component.ScriptComponent;
import net.bestia.messages.internal.entity.EntityComponentMessage;
import net.bestia.zoneserver.actor.zone.ZoneAkkaApi;

public class ScriptComponentInterceptor extends ComponentInterceptor<ScriptComponent> {
	
	private static final Logger LOG = LoggerFactory.getLogger(ScriptComponentInterceptor.class);
	private final ZoneAkkaApi akkaApi;

	@Autowired
	public ScriptComponentInterceptor(ZoneAkkaApi akkaApi) {
		super(ScriptComponent.class);
		
		this.akkaApi = Objects.requireNonNull(akkaApi);
	}

	@Override
	protected void onDeleteAction(EntityService entityService, Entity entity, ScriptComponent comp) {
		// Stop the actor timing the entity component.
		LOG.trace("ScriptComponent deleted. Stopping actor.");

		EntityComponentMessage msg = EntityComponentMessage.stop(comp.getEntityId(), comp.getId());
		akkaApi.sendEntityActor(comp.getEntityId(), msg);
	}

	@Override
	protected void onUpdateAction(EntityService entityService, Entity entity, ScriptComponent comp) {
		// no op.
	}

	@Override
	protected void onCreateAction(EntityService entityService, Entity entity, ScriptComponent comp) {
		// no op.
	}

}
