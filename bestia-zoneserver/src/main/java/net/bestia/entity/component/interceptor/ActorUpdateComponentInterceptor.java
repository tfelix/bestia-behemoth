package net.bestia.entity.component.interceptor;

import net.bestia.entity.Entity;
import net.bestia.entity.EntityService;
import net.bestia.entity.component.Component;
import net.bestia.entity.component.ComponentActor;
import net.bestia.messages.EntityComponentUpdateMessage;
import net.bestia.messages.MessageApi;
import net.bestia.messages.entity.ComponentEnvelope;

import java.util.Objects;

/**
 * This interceptor will check if the component was annotated to notify certain actors
 * if the component value has changed. The actor will then get notified.
 */
public class ActorUpdateComponentInterceptor extends BaseComponentInterceptor<Component> {

	private final MessageApi msgApi;

	public ActorUpdateComponentInterceptor(MessageApi msgApi) {
		super(Component.class);

		this.msgApi = Objects.requireNonNull(msgApi);
	}

	@Override
	protected void onDeleteAction(EntityService entityService, Entity entity, Component comp) {
		final ComponentActor syncActor = comp.getClass().getAnnotation(ComponentActor.class);
		if(dontUpdateActor(syncActor)) {
			return;
		}
		final EntityComponentUpdateMessage msg = new EntityComponentUpdateMessage();
		final ComponentEnvelope envelope = new ComponentEnvelope(entity.getId(), comp.getId(), msg);
		msgApi.sendToEntity(envelope);
	}

	@Override
	protected void onUpdateAction(EntityService entityService, Entity entity, Component comp) {
		final ComponentActor syncActor = comp.getClass().getAnnotation(ComponentActor.class);
		if(dontUpdateActor(syncActor)) {
			return;
		}
		final EntityComponentUpdateMessage msg = new EntityComponentUpdateMessage();
		final ComponentEnvelope envelope = new ComponentEnvelope(entity.getId(), comp.getId(), msg);
		msgApi.sendToEntity(envelope);
	}

	@Override
	protected void onCreateAction(EntityService entityService, Entity entity, Component comp) {
		final ComponentActor syncActor = comp.getClass().getAnnotation(ComponentActor.class);
		if(dontUpdateActor(syncActor)) {
			return;
		}
		final EntityComponentUpdateMessage msg = new EntityComponentUpdateMessage();
		final ComponentEnvelope envelope = new ComponentEnvelope(entity.getId(), comp.getId(), msg);
		msgApi.sendToEntity(envelope);
	}

	private boolean dontUpdateActor(ComponentActor syncActor) {
		return syncActor == null || !syncActor.updateActorOnChange();
	}
}
