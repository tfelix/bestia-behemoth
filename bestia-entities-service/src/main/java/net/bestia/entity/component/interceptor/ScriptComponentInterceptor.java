package net.bestia.entity.component.interceptor;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.bestia.entity.Entity;
import net.bestia.entity.EntityService;
import net.bestia.entity.component.ScriptComponent;
import net.bestia.messages.MessageApi;
import net.bestia.messages.internal.entity.EntityComponentStateMessage;

/**
 * This cleans only up if the script component gets removed. Not all script
 * components will lead to the installation of a PeriodicScriptActor. Only when
 * called via the script this actor is created. Removal must be done
 * automatically. Thats the job of this interceptor.
 * 
 * @author Thomas Felix
 *
 */
@Component
public class ScriptComponentInterceptor extends BaseComponentInterceptor<ScriptComponent> {

	private static final Logger LOG = LoggerFactory.getLogger(ScriptComponentInterceptor.class);
	private final MessageApi akkaApi;

	@Autowired
	public ScriptComponentInterceptor(MessageApi akkaApi) {
		super(ScriptComponent.class);

		this.akkaApi = Objects.requireNonNull(akkaApi);
	}

	@Override
	protected void onDeleteAction(EntityService entityService, Entity entity, ScriptComponent comp) {
		// Stop the actor timing the entity component.
		LOG.debug("Component {} is deleted.", comp);

		EntityComponentStateMessage msg = EntityComponentStateMessage.remove(comp.getEntityId(), comp.getId());
		akkaApi.sendToEntity(msg);
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
