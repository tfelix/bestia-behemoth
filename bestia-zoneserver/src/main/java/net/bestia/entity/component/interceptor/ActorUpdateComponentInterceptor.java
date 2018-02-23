package net.entity.component.interceptor;

import net.bestia.entity.component.interceptor.BaseComponentInterceptor;
import net.entity.Entity;
import net.bestia.entity.EntityService;
import net.bestia.entity.component.Component;
import net.bestia.entity.component.ComponentActor;
import bestia.messages.ComponentChangedMessage;
import net.bestia.messages.ComponentMessageEnvelope;
import bestia.messages.MessageApi;

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
    if (dontUpdateActor(syncActor)) {
      return;
    }
    updateActorComponent(entity, comp);
  }

  @Override
  protected void onUpdateAction(EntityService entityService, Entity entity, Component comp) {
    final ComponentActor syncActor = comp.getClass().getAnnotation(ComponentActor.class);
    if (dontUpdateActor(syncActor)) {
      return;
    }
    updateActorComponent(entity, comp);
  }

  @Override
  protected void onCreateAction(EntityService entityService, Entity entity, Component comp) {
    final ComponentActor syncActor = comp.getClass().getAnnotation(ComponentActor.class);
    if (dontUpdateActor(syncActor)) {
      return;
    }
    updateActorComponent(entity, comp);
  }

  private void updateActorComponent(Entity entity, Component comp) {
    final ComponentChangedMessage msg = new ComponentChangedMessage();
    final ComponentMessageEnvelope envelope = new ComponentMessageEnvelope(entity.getId(), comp.getId(), msg);
    msgApi.sendToEntity(entity.getId(), envelope);
  }

  private boolean dontUpdateActor(ComponentActor syncActor) {
    return syncActor == null || !syncActor.updateActorOnChange();
  }
}
