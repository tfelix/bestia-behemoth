package net.bestia.zoneserver.actor.entity;

import akka.actor.ActorRef;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.entity.Entity;
import net.bestia.entity.EntityService;
import net.bestia.entity.component.ComponentSync;
import net.bestia.entity.component.EntityComponentSyncMessageFactory;
import net.bestia.entity.component.PositionComponent;
import net.bestia.entity.component.SyncType;
import net.bestia.messages.entity.EntityComponentSyncMessage;
import net.bestia.messages.entity.EntitySyncRequestMessage;
import net.bestia.model.geometry.Point;
import net.bestia.model.geometry.Rect;
import net.bestia.zoneserver.actor.SpringExtension;
import net.bestia.zoneserver.actor.zone.ClientMessageDigestActor;
import net.bestia.zoneserver.actor.zone.SendClientActor;
import net.bestia.zoneserver.actor.zone.SendClientsInRangeActor;
import net.bestia.zoneserver.entity.EntitySearchService;
import net.bestia.zoneserver.entity.PlayerEntityService;
import net.bestia.zoneserver.map.MapService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * This actor listens to engine requests to perform a full entity
 * synchronization in the visible update rect. This is used if the engine thinks
 * it needs a complete update (maybe after a reload or mapload).
 *
 * @author Thomas Felix
 */
@Component
@Scope("prototype")
public class EntitySyncRequestActor extends ClientMessageDigestActor {

  private final LoggingAdapter LOG = Logging.getLogger(getContext().getSystem(), this);

  public static final String NAME = "entitySync";

  private final EntityService entityService;
  private final PlayerEntityService playerEntityService;
  private final EntitySearchService entitySearchService;
  private final ActorRef sendClient;
  private final ActorRef sendAllClients;
  private final EntityComponentSyncMessageFactory messageHelper = new EntityComponentSyncMessageFactory();

  @Autowired
  public EntitySyncRequestActor(EntityService entityService,
                                EntitySearchService entitySearchService,
                                PlayerEntityService playerEntityService) {

    this.entityService = Objects.requireNonNull(entityService);
    this.entitySearchService = Objects.requireNonNull(entitySearchService);
    this.playerEntityService = Objects.requireNonNull(playerEntityService);
    this.sendClient = SpringExtension.actorOf(getContext(), SendClientActor.class);
    this.sendAllClients = SpringExtension.actorOf(getContext(), SendClientsInRangeActor.class);
    redirectConfig.match(EntitySyncRequestMessage.class, this::onSyncRequest);
  }

  private void onSyncRequest(EntitySyncRequestMessage msg) {

    final long requestAccId = msg.getAccountId();

    LOG.debug("Account {} requests a full entity sync.", requestAccId);

    final Entity activeEntity = playerEntityService.getActivePlayerEntity(msg.getAccountId());
    final Point activePos = entityService.getComponent(activeEntity, PositionComponent.class)
            .orElseThrow(IllegalArgumentException::new)
            .getPosition();

    final Rect updateRect = MapService.getUpdateRect(activePos);

    final List<net.bestia.entity.component.Component> components = entitySearchService.getCollidingEntities(updateRect)
            .stream()
            .map(entityService::getAllComponents)
            .flatMap(Collection::stream)
            .filter(c -> c.getClass().isAnnotationPresent(ComponentSync.class))
            .collect(Collectors.toList());

    components.forEach(c -> {

      final EntityComponentSyncMessage syncMessage = messageHelper.forComponent(c);
      ComponentSync syncAnno = c.getClass().getAnnotation(ComponentSync.class);

      if (syncAnno.value() == SyncType.ALL) {
        sendAllClients.tell(syncMessage, getSelf());
      } else {
        sendClient.tell(syncMessage, getSelf());
      }
    });
  }
}