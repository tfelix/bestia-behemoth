package net.bestia.entity.component.interceptor;

import net.bestia.entity.component.EntityComponentSyncMessageFactory;
import net.bestia.messages.entity.EntityComponentSyncMessage;
import org.springframework.stereotype.Component;

import net.bestia.entity.Entity;
import net.bestia.entity.EntityService;
import net.bestia.entity.component.LevelComponent;
import net.bestia.entity.component.PlayerComponent;
import net.bestia.messages.MessageApi;
import net.bestia.messages.component.LevelComponentExMessage;
import net.bestia.messages.component.LevelComponentMessage;

/**
 * Intercepts the level component and will send a special level message
 * depending if the component is attached and send to the owner or to all
 * others.
 *
 * @author Thomas Felix
 */
@Component
public class LevelComponentInterceptor extends BaseComponentInterceptor<LevelComponent> {

  private final MessageApi msgApi;
  private final EntityComponentSyncMessageFactory syncMessageFactory;

  LevelComponentInterceptor(MessageApi msgApi,
                            EntityComponentSyncMessageFactory syncMessageFactory) {
    super(LevelComponent.class);

    this.syncMessageFactory = syncMessageFactory;
    this.msgApi = msgApi;
  }

  @Override
  protected void onDeleteAction(EntityService entityService, Entity entity, LevelComponent comp) {
    // no op.
  }

  @Override
  protected void onUpdateAction(EntityService entityService, Entity entity, LevelComponent comp) {
    updateClientsWithLevel(entityService, entity, comp);
  }

  @Override
  protected void onCreateAction(EntityService entityService, Entity entity, LevelComponent comp) {
    updateClientsWithLevel(entityService, entity, comp);
  }

  private void updateClientsWithLevel(EntityService entityService, Entity entity, LevelComponent comp) {
    final long ownerAccId = entityService.getComponent(entity, PlayerComponent.class)
            .map(PlayerComponent::getOwnerAccountId)
            .orElse(0L);

    final LevelComponentMessage lcm = new LevelComponentMessage(comp.getLevel());
    final LevelComponentExMessage exLcm = new LevelComponentExMessage(comp.getLevel(), comp.getExp());

    if (ownerAccId != 0) {
      final EntityComponentSyncMessage msg = syncMessageFactory.forCustomComponentPayload(entity.getId(),
              LevelComponent.class, exLcm);
      msgApi.sendToClient(ownerAccId, msg);
    }

    final EntityComponentSyncMessage msg = syncMessageFactory.forCustomComponentPayload(entity.getId(),
            LevelComponent.class, lcm);
    msgApi.sendToActiveClientsInRange(entity.getId(), msg);
  }
}